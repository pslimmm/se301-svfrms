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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActuatorServiceTest {
    @Test
    @org.junit.jupiter.api.Timeout(10)
    void fatalConsumerFailureWakesWaitingProducerAndRetainsCause() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var original = new AssertionError("fatal actuator failure");
        var fatal = new org.example.svfrms.command.AbstractDeviceCommand(
                org.example.svfrms.domain.actuator.ActuatorType.WATER_PUMP, "fatal") {
            @Override public ChamberSnapshot execute(ChamberStateTracker tracker) {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw original;
            }
            @Override public String stateDescription(ChamberSnapshot state) { return ""; }
        };
        var service = new ActuatorExecutionService(new BoundedControlQueue(1),
                new java.util.concurrent.ConcurrentHashMap<>(), new java.util.concurrent.ConcurrentHashMap<>(), line -> { });
        var executor = Executors.newSingleThreadExecutor();
        var id = new ChamberId("CH1");
        service.start();
        try {
            assertThat(service.submit(new ActuatorRequest(id, fatal), Duration.ZERO)).isTrue();
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(service.submit(new ActuatorRequest(id, new WaterPumpCommand()), Duration.ZERO)).isTrue();
            var waiting = executor.submit(() -> service.submit(new ActuatorRequest(id, new WaterPumpCommand()), Duration.ofSeconds(5)));
            release.countDown();
            assertThatThrownBy(() -> waiting.get(2, TimeUnit.SECONDS)).hasRootCause(original);
            assertThatThrownBy(() -> service.awaitIdle(Duration.ofSeconds(1))).hasRootCause(original);
            assertThatThrownBy(() -> service.close(Duration.ofSeconds(1))).hasRootCause(original);
            assertThat(service.failure()).containsSame(original);
        } finally {
            release.countDown();
            executor.shutdownNow();
            try { service.close(Duration.ofSeconds(1)); } catch (IllegalStateException expectedFailure) { }
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void defaultQueueRetainsLegacyCapacityFive() {
        assertThat(new BoundedControlQueue().remainingCapacity()).isEqualTo(5);
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void throwingCommandAndOutputFailureRemainObservable() throws Exception {
        var id = new ChamberId("CH1");
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var later = new CountDownLatch(1);
        var original = new IllegalArgumentException("broken command");
        var command = new org.example.svfrms.command.AbstractDeviceCommand(org.example.svfrms.domain.actuator.ActuatorType.WATER_PUMP,"failure") {
            @Override public ChamberSnapshot execute(ChamberStateTracker state) {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw original;
            }
            @Override public String stateDescription(ChamberSnapshot s) { return ""; }
        };
        var valid = new org.example.svfrms.command.AbstractDeviceCommand(org.example.svfrms.domain.actuator.ActuatorType.WATER_PUMP,"later") {
            @Override public ChamberSnapshot execute(ChamberStateTracker state) { later.countDown(); return state.snapshot(); }
            @Override public String stateDescription(ChamberSnapshot s) { return "done"; }
        };
        var service = new ActuatorExecutionService(new BoundedControlQueue(1),
                new java.util.concurrent.ConcurrentHashMap<>(),new java.util.concurrent.ConcurrentHashMap<>(),new ResourceAccountingService(),
                line -> { if (line.startsWith("Actuator command failed:")) throw new IllegalStateException("output broken"); });
        service.start();
        try {
            service.submit(new ActuatorRequest(id, command),Duration.ofSeconds(1));
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            service.submit(new ActuatorRequest(id, valid),Duration.ofSeconds(1));
            release.countDown();
            assertThat(later.await(1,TimeUnit.SECONDS)).isTrue();
            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.awaitIdle(Duration.ofSeconds(1)));
            assertThat(service.failure()).containsSame(original);
            assertThat(original.getSuppressed()).extracting(Throwable::getMessage).containsExactly("output broken");
            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.submit(new ActuatorRequest(id, valid),Duration.ZERO));
        } finally {
            release.countDown();
            try { service.close(); } catch (IllegalStateException expected) { }
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void fullQueueTimesOutAndLateAdmissionIsRejected() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var id = new ChamberId("CH1");
        var service = new ActuatorExecutionService(new BoundedControlQueue(1),
                new java.util.concurrent.ConcurrentHashMap<>(),new java.util.concurrent.ConcurrentHashMap<>(),line -> {
                    if (line.contains("Water Pump Activated")) {
                        entered.countDown();
                        try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                });
        var executor = Executors.newFixedThreadPool(2);
        service.start();
        try {
            var request = new ActuatorRequest(id,new WaterPumpCommand());
            assertThat(service.submit(request,Duration.ZERO)).isTrue();
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            assertThat(service.submit(request,Duration.ZERO)).isTrue();
            assertThat(service.submit(request,Duration.ofMillis(20))).isFalse();
            // This admission is waiting for space. Starting close must wake/reject it before drain.
            var pending = executor.submit(() -> service.submit(request,Duration.ofSeconds(5)));
            var closing = executor.submit(() -> { service.close(Duration.ofSeconds(1)); return null; });
            assertThatThrownBy(() -> pending.get(1,TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.submit(request,Duration.ZERO)).isInstanceOf(IllegalStateException.class);
            release.countDown();
            closing.get(2,TimeUnit.SECONDS);
            assertThat(service.isTerminated()).isTrue();
        } finally {
            release.countDown(); executor.shutdownNow();
            try { service.close(Duration.ofSeconds(1)); } catch (IllegalStateException expected) { }
            assertThat(executor.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void awaitIdleIncludesExecutingWorkAndTimeoutCloseRetainsFailure() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = new ActuatorExecutionService(new BoundedControlQueue(1),
                new java.util.concurrent.ConcurrentHashMap<>(),new java.util.concurrent.ConcurrentHashMap<>(),line -> {
                    entered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try { release.await(); break; } catch (InterruptedException e) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                });
        service.start();
        try {
            service.submit(new ActuatorRequest(new ChamberId("CH1"),new WaterPumpCommand()),Duration.ZERO);
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.awaitIdle(Duration.ofMillis(20))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Timed out");
            assertThatThrownBy(() -> service.close(Duration.ofMillis(50))).isInstanceOf(IllegalStateException.class).hasMessageContaining("did not terminate");
            assertThat(service.failure()).isPresent();
            assertThatThrownBy(() -> service.close(Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class).hasMessageContaining("did not terminate");
        } finally {
            release.countDown();
            // A non-cooperative callback can only stop after its owner releases it.
            var deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
            while (!service.isTerminated() && System.nanoTime()<deadline) Thread.onSpinWait();
            assertThat(service.isTerminated()).isTrue();
        }
    }
    @Test
    void commandsPreserveLegacyClampsAndOutput() throws Exception {
        var commands = java.util.List.<org.example.svfrms.command.DeviceCommand>of(new CoolingFanCommand(), new WaterPumpCommand(),
                new org.example.svfrms.command.NutrientInjectorCommand(), new org.example.svfrms.command.HumidifierCommand(),
                new org.example.svfrms.command.DehumidifierCommand(), new org.example.svfrms.command.GrowLightCommand());
        for (double value : new double[]{-50, 18, 23, 30, 40, 80, 85, 90, 100, 850, 1000, 1100}) {
            for (var command : commands) {
                ChamberId id = new ChamberId("TEST");
                var state = new ChamberStateTracker(new ChamberSnapshot(value,value,value,value,value));
                var summary = new ChamberSummaryTracker();
                var output = new java.util.concurrent.CopyOnWriteArrayList<String>();
                var metrics = new ResourceAccountingService();
                var service = new ActuatorExecutionService(new BoundedControlQueue(), Map.of(id,state),Map.of(id,summary),metrics,output::add);
                service.start();
                for (int repeat = 0; repeat < 3; repeat++) {
                    var before = state.snapshot();
                    double t=before.temperature(), h=before.humidity(), s=before.soilMoisture(), l=before.lightLevel(), n=before.nutrientLevel();
                    switch (command.type().orElseThrow()) {
                        case COOLING_FAN -> t=Math.max(18,t-5);
                        case WATER_PUMP -> s=Math.min(100,s+20);
                        case NUTRIENT_INJECTOR -> n=Math.min(100,n+15);
                        case HUMIDIFIER -> h=Math.min(100,h+10);
                        case DEHUMIDIFIER -> h=Math.max(30,h-10);
                        case GROW_LIGHT -> l=Math.min(1000,l+150);
                    }
                    var expected = new ChamberSnapshot(t,h,s,l,n);
                    service.submit(new ActuatorRequest(id,command),Duration.ofSeconds(1));
                    service.awaitIdle(Duration.ofSeconds(2));
                    assertThat(state.snapshot()).isEqualTo(expected);
                    assertThat(output.subList(repeat*2,repeat*2+2)).containsExactly("\t\u001B[32m[TEST][CONTROL] " + command.actionName() + "\u001B[0m", "\t\u001B[32m[TEST][CONTROL] " + command.stateDescription(expected) + "\u001B[0m");
                }
                service.close();
                assertThat(summary.snapshot().mitigationActions()).isEqualTo(3);
                assertThat(metrics.waterUsage()).isZero(); assertThat(metrics.energyUsage()).isZero();
            }
        }
    }

    @Test
    void commandDescriptionUsesCommittedStateEvenWhenSensorChangesLater() throws Exception {
        var id = new ChamberId("CH1");
        var state = new ChamberStateTracker(new ChamberSnapshot(30,70,60,600,70));
        var output = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var command = new org.example.svfrms.command.AbstractDeviceCommand(org.example.svfrms.domain.actuator.ActuatorType.COOLING_FAN,"Cooling Fan Activated") {
            @Override public ChamberSnapshot execute(ChamberStateTracker tracker) {
                var committed = new CoolingFanCommand().execute(tracker);
                tracker.updateAndGet(s -> new ChamberSnapshot(99,s.humidity(),s.soilMoisture(),s.lightLevel(),s.nutrientLevel()));
                return committed;
            }
            @Override public String stateDescription(ChamberSnapshot s) { return new CoolingFanCommand().stateDescription(s); }
        };
        var service = new ActuatorExecutionService(new BoundedControlQueue(),Map.of(id,state),Map.of(id,new ChamberSummaryTracker()),new ResourceAccountingService(),output::add);
        service.start(); service.submit(new ActuatorRequest(id,command),Duration.ofSeconds(1)); service.close();
        assertThat(state.snapshot().temperature()).isEqualTo(99);
        assertThat(output.get(1)).contains("Temperature reduced to 25.0C");
    }
    @Test
    void directCommandsUpdateChamberWithoutResourceCharges() {
        ChamberStateTracker tracker = new ChamberStateTracker(new ChamberSnapshot(20, 70, 90, 600, 70));
        ResourceAccountingService metrics = new ResourceAccountingService();

        new CoolingFanCommand().execute(tracker);
        new WaterPumpCommand().execute(tracker);

        assertThat(tracker.snapshot().temperature()).isEqualTo(18);
        assertThat(tracker.snapshot().soilMoisture()).isEqualTo(100);
        assertThat(metrics.energyUsage()).isZero();
        assertThat(metrics.waterUsage()).isZero();
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
        assertThat(metrics.waterUsage()).isZero();
        assertThat(service.isTerminated()).isTrue();
    }

    @Test
    void closeDrainsAcceptedCommandsExactlyOnce() throws Exception {
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
