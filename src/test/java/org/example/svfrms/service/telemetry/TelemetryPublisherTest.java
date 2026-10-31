package org.example.svfrms.service.telemetry;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryPublisherTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void fullQueueFailureReachesProducerFutureAfterAlertAndCharge() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var chamber=new ChamberId("CH1");
        var states=Map.of(chamber,ChamberStateTracker.initial());
        var summaries=Map.of(chamber,new ChamberSummaryTracker());
        var service=new ActuatorExecutionService(new BoundedControlQueue(1),states,summaries,line -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        var resources=new ResourceAccountingService(); var alerts=new AlertManagementService();
        var registry=CropRegistry.withStandardProfiles(); var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        var publisher=new TelemetryPublisher(failure -> { throw failure; });
        publisher.subscribe(new ThresholdEvaluationObserver(registry,new DefaultCropThresholdStrategy(registry,clock),alerts,service,resources,states,summaries,Duration.ofMillis(20),s -> {}));
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        service.start();
        var reading=new SensorReading(chamber,"Tomato",SensorType.TEMPERATURE,29,NOW);
        try {
            publisher.publish(reading);
            assertThat(entered.await(1,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            publisher.publish(reading);
            var producer=executor.submit(() -> publisher.publish(reading));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> producer.get(1,java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class).hasRootCauseMessage("Actuator queue remained full until timeout");
            assertThat(alerts.generatedAlertCount()).isEqualTo(3);
            assertThat(resources.energyUsage()).isEqualTo(30);
        } finally { release.countDown(); executor.shutdownNow(); service.close(Duration.ofSeconds(1)); assertThat(executor.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void sensorLogsAreStoredUntilExplicitDisplay() throws Exception {
        var lines = new java.util.ArrayList<String>();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        try (var context = new org.example.svfrms.infrastructure.context.SvfrmsApplicationContext(
                java.nio.file.Path.of("target/task3-report.txt"), clock, lines::add, 42)) {
            context.telemetry().publish(new SensorReading(new ChamberId("CH1"), "Unknown", SensorType.SOIL_MOISTURE, 61, NOW));
            context.telemetry().publish(new SensorReading(new ChamberId("CH1"), "Unknown", SensorType.TEMPERATURE, 25, NOW));
            assertThat(lines).isEmpty();
            assertThat(context.sensorLogs()).containsExactly(new java.util.Date(clock.millis()) + " : Processing Unknown Soil = 61.0",
                    new java.util.Date(clock.millis()) + " : Processing Unknown Temperature = 25.0");
            context.showLogs();
            assertThat(lines).containsExactlyElementsOf(context.sensorLogs());
            assertThat(context.sensorLogs()).hasSize(2);
        }
    }

    @Test
    void publishesToLoggerAndThresholdObserverWithoutCoupling() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24, 70, 60, 600, 70));
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        ResourceAccountingService metrics = new ResourceAccountingService();
        CropRegistry crops = CropRegistry.withStandardProfiles();
        AlertManagementService alerts = new AlertManagementService();
        ActuatorExecutionService actuators = new ActuatorExecutionService(
                new BoundedControlQueue(), Map.of(chamber, state), Map.of(chamber, summary), metrics, ignored -> {});
        actuators.start();
        List<String> log = new CopyOnWriteArrayList<>();
        TelemetryPublisher publisher = new TelemetryPublisher();
        publisher.subscribe(new ConsoleLoggingObserver(log::add, Clock.fixed(NOW, ZoneOffset.UTC)));
        publisher.subscribe(new ThresholdEvaluationObserver(crops,
                new DefaultCropThresholdStrategy(crops, Clock.fixed(NOW, ZoneOffset.UTC)),
                alerts, actuators, metrics, Map.of(chamber, state), Map.of(chamber, summary), Duration.ofSeconds(1), ignored -> {}));

        try {
            publisher.publish(new SensorReading(chamber, "Tomato", SensorType.TEMPERATURE, 29, NOW));
        } finally {
            actuators.close();
        }

        assertThat(log).singleElement().asString().endsWith(" : Processing Tomato Temperature = 29.0");
        assertThat(alerts.getActiveAlertsSnapshot()).singleElement().satisfies(alert ->
                assertThat(alert.message()).isEqualTo("CH1 Tomato High Temp"));
        assertThat(summary.snapshot().alerts()).isEqualTo(1);
        assertThat(summary.snapshot().mitigationActions()).isEqualTo(1);
        assertThat(state.snapshot().temperature()).isEqualTo(24);
        assertThat(alerts.generatedAlertCount()).isEqualTo(1);
        assertThat(metrics.energyUsage()).isEqualTo(10);
    }

    @org.junit.jupiter.api.RepeatedTest(20)
    @org.junit.jupiter.api.Timeout(10)
    void subscribersCanBeAddedAndRemovedConcurrentlyWithPublishing() throws Exception {
        TelemetryPublisher publisher = new TelemetryPublisher();
        AtomicInteger stableCalls = new AtomicInteger();
        AtomicInteger optionalCalls = new AtomicInteger();
        publisher.subscribe(ignored -> stableCalls.incrementAndGet());
        TelemetryObserver optional = ignored -> optionalCalls.incrementAndGet();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var publishing = pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    barrier.await(2, java.util.concurrent.TimeUnit.SECONDS);
                    publisher.publish(reading());
                }
                return null;
            });
            var mutation = pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    barrier.await(2, java.util.concurrent.TimeUnit.SECONDS);
                    publisher.subscribe(optional);
                    publisher.unsubscribe(optional);
                }
                return null;
            });
            publishing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            mutation.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(stableCalls).hasValue(1000);
        assertThat(optionalCalls.get()).isBetween(0,1000);
        publisher.publish(reading());
        assertThat(stableCalls).hasValue(1001);
    }

    private static SensorReading reading() {
        return new SensorReading(new ChamberId("CH1"), "Tomato", SensorType.TEMPERATURE, 24, NOW);
    }
}
