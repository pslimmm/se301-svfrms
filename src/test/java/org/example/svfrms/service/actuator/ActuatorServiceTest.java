package org.example.svfrms.service.actuator;

import org.example.svfrms.command.CoolingFanCommand;
import org.example.svfrms.command.WaterPumpCommand;
import org.example.svfrms.concurrency.ActuatorRequest;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ActuatorServiceTest {
    @Test
    void commandsUpdateChamberAndRecordLegacyResourceDeltas() {
        ChamberStateTracker tracker = new ChamberStateTracker(new ChamberSnapshot(20, 70, 90, 600, 70));
        ResourceAccountingService metrics = new ResourceAccountingService();

        new CoolingFanCommand().execute(tracker, metrics);
        new WaterPumpCommand().execute(tracker, metrics);

        assertThat(tracker.snapshot().temperature()).isEqualTo(18);
        assertThat(tracker.snapshot().soilMoisture()).isEqualTo(100);
        assertThat(metrics.energyUsage()).isEqualTo(10);
        assertThat(metrics.waterUsage()).isEqualTo(50);
    }

    @Test
    void boundedQueueAppliesBackpressureAndReturnsRequestsInOrder() throws Exception {
        BoundedControlQueue queue = new BoundedControlQueue(1);
        ActuatorRequest request = new ActuatorRequest(new ChamberId("CH1"), new WaterPumpCommand());

        assertThat(queue.offer(request, Duration.ZERO)).isTrue();
        assertThat(queue.offer(request, Duration.ZERO)).isFalse();
        assertThat(queue.take()).isEqualTo(request);
    }

    @Test
    void workerRoutesCommandsAndStopsGracefullyWithPoisonPill() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24, 70, 40, 600, 70));
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        ResourceAccountingService metrics = new ResourceAccountingService();
        ActuatorExecutionService service = new ActuatorExecutionService(
                new BoundedControlQueue(), Map.of(chamber, state), Map.of(chamber, summary), metrics, ignored -> {});
        service.start();
        assertThat(service.submit(new ActuatorRequest(chamber, new WaterPumpCommand()), Duration.ofSeconds(1))).isTrue();

        service.close();

        assertThat(state.snapshot().soilMoisture()).isEqualTo(60);
        assertThat(summary.snapshot().mitigationActions()).isEqualTo(1);
        assertThat(summary.snapshot().lastAction()).isEqualTo("Water Pump Activated");
        assertThat(metrics.waterUsage()).isEqualTo(50);
        assertThat(service.isTerminated()).isTrue();
    }

    @Test
    void concurrentSubmissionsAcceptedBeforeCloseAreProcessedAndLaterSubmissionsRejected() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24, 70, 40, 600, 70));
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        ActuatorExecutionService service = new ActuatorExecutionService(
                new BoundedControlQueue(16), Map.of(chamber, state), Map.of(chamber, summary),
                new ResourceAccountingService(), ignored -> {});
        service.start();
        var executor = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        try {
            var producers = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int producer = 0; producer < 8; producer++) {
                producers.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < 500; index++) {
                        try {
                            if (service.submit(new ActuatorRequest(chamber, new WaterPumpCommand()), Duration.ofMillis(2))) {
                                accepted.incrementAndGet();
                            }
                        } catch (IllegalStateException closing) {
                            break;
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            service.close();
            for (var producer : producers) producer.get(5, TimeUnit.SECONDS);

            assertThat(summary.snapshot().mitigationActions()).isEqualTo(accepted.get());
            assertThat(service.isTerminated()).isTrue();
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> service.submit(new ActuatorRequest(chamber, new WaterPumpCommand()), Duration.ZERO));
        } finally {
            executor.shutdownNow();
            if (!service.isTerminated()) service.close();
        }
    }

    @Test
    void interruptedCloseStillStopsWorkerAndRestoresInterruptStatus() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ActuatorExecutionService service = new ActuatorExecutionService(
                new BoundedControlQueue(),
                Map.of(chamber, new ChamberStateTracker(new ChamberSnapshot(24, 70, 40, 600, 70))),
                Map.of(chamber, new ChamberSummaryTracker()), new ResourceAccountingService(), ignored -> {});
        service.start();

        Thread.currentThread().interrupt();
        try {
            assertThatExceptionOfType(InterruptedException.class).isThrownBy(service::close);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(service.isTerminated()).isTrue();
        } finally {
            Thread.interrupted();
            if (!service.isTerminated()) service.close();
        }
    }
}
