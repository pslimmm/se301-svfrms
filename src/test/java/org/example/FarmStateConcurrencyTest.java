package org.example;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FarmStateConcurrencyTest {
    @Test
    void freshApplicationsDoNotShareDataOrQueues() {
        SVFRMS first = quietApplication();
        SVFRMS second = quietApplication();
        first.processSensor("CH1", "Tomato", "Soil", 30);
        first.activateControl("CH1", "Water Pump");
        first.archiveAlerts();
        first.generateDailyReport();
        first.stopControlThread();

        assertTrue(second.snapshot().chamberStates().isEmpty());
        assertTrue(second.snapshot().chamberSummaries().isEmpty());
        assertTrue(second.snapshot().alerts().isEmpty());
        assertTrue(second.snapshot().archivedAlerts().isEmpty());
        assertTrue(second.snapshot().sensorLogs().isEmpty());
        assertTrue(second.snapshot().reports().isEmpty());
        assertEquals(0, second.snapshot().totalWaterUsage());
        assertEquals(0, second.snapshot().totalEnergyUsage());
        assertEquals(0, second.snapshot().totalAlertsGenerated());
        assertTrue(second.pendingControls().isEmpty());
        second.processSensor("CH2", "Tomato", "Temperature", 20);
        second.addControlRequest("CH2", DeviceType.GROW_LIGHT);
        assertEquals("CH1", first.pendingControls().getFirst().chamberId());
        assertEquals("CH2", second.pendingControls().getFirst().chamberId());
        assertFalse(first.snapshot().chamberStates().containsKey("CH2"));
    }

    @Test
    void snapshotsAreDeeplyImmutableAndDetachedFromLaterUpdates() {
        FarmState state = new FarmState();
        state.processSensor("CH1", "Tomato", "Temperature", 20);
        var before = state.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> before.alerts().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.archivedAlerts().add("injected"));
        assertThrows(UnsupportedOperationException.class, () -> before.sensorLogs().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.reports().add("injected"));
        assertThrows(UnsupportedOperationException.class, () -> before.chamberStates().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.chamberSummaries().clear());

        state.activateControl("CH1", "Cooling Fan");
        state.archiveAlerts();
        state.dailyReportSnapshot();
        assertEquals(20, before.chamberStates().get("CH1").temperature());
        assertEquals(0, before.chamberSummaries().get("CH1").mitigationActions());
        assertEquals(1, before.totalAlerts());
        assertTrue(before.archivedAlerts().isEmpty());
        assertTrue(before.reports().isEmpty());
        assertEquals(18, state.snapshot().chamberStates().get("CH1").temperature());
        assertEquals(1, state.snapshot().archivedAlerts().size());

        SVFRMS app = quietApplication();
        app.addControlRequest("CH1", DeviceType.WATER_PUMP);
        assertThrows(UnsupportedOperationException.class, () -> app.pendingControls().clear());
    }

    @Test
    void simultaneousFirstUseRetainsOneChamberAndEveryAlertAndAction() throws Exception {
        FarmState state = new FarmState();
        runTogether(IntStream.range(0, 4).mapToObj(worker -> (Runnable) () -> {
            for (int i = 0; i < 250; i++) {
                state.processSensor("shared", "Tomato", "Temperature", 20);
                state.activateControl("shared", "Cooling Fan");
            }
        }).toList());
        var snapshot = state.snapshot();
        assertEquals(1, snapshot.chamberStates().size());
        assertEquals(1, snapshot.chamberSummaries().size());
        assertEquals(1000, snapshot.totalAlertsGenerated());
        assertEquals(1000, snapshot.sensorLogs().size());
        assertEquals(15000, snapshot.totalEnergyUsage());
        assertEquals(1000, snapshot.chamberSummaries().get("shared").mitigationActions());
        assertEquals(18, snapshot.chamberStates().get("shared").temperature());
        assertAlertInvariants(snapshot);
    }

    @Test
    void concurrentUsagePairsHaveExactTotalsAndCoherentSnapshots() throws Exception {
        FarmState state = new FarmState();
        List<Runnable> tasks = new ArrayList<>();
        for (int worker = 0; worker < 4; worker++) {
            tasks.add(() -> {
                for (int i = 0; i < 1000; i++) state.addUsage(2, 1);
            });
        }
        tasks.add(() -> {
            for (int i = 0; i < 1000; i++) {
                var snapshot = state.snapshot();
                assertEquals(2 * snapshot.totalEnergyUsage(), snapshot.totalWaterUsage());
            }
        });
        runTogether(tasks);
        assertEquals(8000, state.snapshot().totalWaterUsage());
        assertEquals(4000, state.snapshot().totalEnergyUsage());
    }

    @Test
    void controlEffectsAndTheirSummariesAreOneTransaction() throws Exception {
        FarmState state = new FarmState();
        // Stay below the clamp so every +20 transformation is measurable, even under contention.
        state.processSensor("CH1", "Unknown", "Soil", -100000);
        List<Runnable> tasks = new ArrayList<>();
        for (int worker = 0; worker < 4; worker++) {
            tasks.add(() -> {
                for (int i = 0; i < 1000; i++) state.activateControl("CH1", "Water Pump");
            });
        }
        tasks.add(() -> {
            for (int i = 0; i < 1000; i++) {
                var snapshot = state.snapshot();
                var summary = snapshot.chamberSummaries().get("CH1");
                int actions = summary == null ? 0 : summary.mitigationActions();
                assertEquals(-100000 + actions * 20, snapshot.chamberStates().get("CH1").soilMoisture());
            }
        });
        runTogether(tasks);
        assertEquals(4000, state.summary("CH1").mitigationActions());
        assertEquals(-20000, state.chamber("CH1").soilMoisture());
    }

    @Test
    void phasedProducersArchiveAndReportsNeverLoseOrPartiallyExposeAlerts() throws Exception {
        FarmState state = new FarmState();
        CyclicBarrier phase = new CyclicBarrier(4);
        List<Runnable> tasks = new ArrayList<>();
        for (int producer = 0; producer < 2; producer++) {
            int id = producer;
            tasks.add(() -> {
                for (int i = 0; i < 50; i++) {
                    await(phase);
                    state.processSensor("CH-" + id + "-" + i, "Tomato", "Temperature", 20);
                    await(phase);
                }
            });
        }
        tasks.add(() -> {
            for (int i = 0; i < 50; i++) {
                await(phase);
                state.archiveAlerts();
                await(phase);
            }
        });
        tasks.add(() -> {
            for (int i = 0; i < 50; i++) {
                await(phase);
                var report = state.dailyReportSnapshot();
                assertAlertInvariants(report);
                assertEquals(report.totalAlertsGenerated(), report.sensorLogs().size());
                assertEquals(report.totalAlertsGenerated() * 15, report.totalEnergyUsage());
                assertEquals("Daily Report", report.reports().getLast());
                await(phase);
            }
        });
        runTogether(tasks);
        state.archiveAlerts();
        var snapshot = state.snapshot();
        assertAlertInvariants(snapshot);
        assertEquals(100, snapshot.totalAlertsGenerated());
        assertEquals(0, snapshot.totalAlerts());
        assertEquals(100, new HashSet<>(snapshot.archivedAlerts()).size());
        assertEquals(100, snapshot.sensorLogs().size());
        assertEquals(101, snapshot.reports().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sensor", "control", "report", "archive", "alerts", "logs"})
    void blockedOutputDoesNotHoldTheStateLock(String operation) throws Exception {
        CountDownLatch enteredOutput = new CountDownLatch(1);
        CountDownLatch releaseOutput = new CountDownLatch(1);
        AtomicBoolean block = new AtomicBoolean(false);
        SVFRMS app = new SVFRMS(new Random(301), message -> {
            if (block.get()) {
                enteredOutput.countDown();
                await(releaseOutput);
            }
        });
        app.processSensor("CH1", "Tomato", "Temperature", 20);
        block.set(true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> outputTask = pool.submit(() -> {
                switch (operation) {
                    case "sensor" -> app.processSensor("CH1", "Tomato", "Temperature", 20);
                    case "control" -> app.activateControl("CH1", "Water Pump");
                    case "report" -> app.generateDailyReport();
                    case "archive" -> app.archiveAlerts();
                    case "alerts" -> app.showAlerts();
                    case "logs" -> app.showLogs();
                    default -> fail(operation);
                }
            });
            assertTrue(enteredOutput.await(5, TimeUnit.SECONDS));
            Future<FarmState.Snapshot> inspection = pool.submit(() -> {
                app.irrigationCycle();
                return app.snapshot();
            });
            var snapshot = inspection.get(5, TimeUnit.SECONDS);
            assertEquals(100, snapshot.totalWaterUsage());
            assertAlertInvariants(snapshot);
            switch (operation) {
                case "sensor" -> assertEquals(2, snapshot.totalAlertsGenerated());
                case "control" -> {
                    assertEquals(80, snapshot.chamberStates().get("CH1").soilMoisture());
                    assertEquals(1, snapshot.chamberSummaries().get("CH1").mitigationActions());
                }
                case "report" -> assertEquals(List.of("Daily Report"), snapshot.reports());
                case "archive" -> assertEquals(1, snapshot.archivedAlerts().size());
                default -> { }
            }
            releaseOutput.countDown();
            outputTask.get(5, TimeUnit.SECONDS);
        } finally {
            releaseOutput.countDown();
            shutdown(pool);
        }
    }

    @Test
    void producerWaitingForQueueCapacityDoesNotHoldTheStateLock() throws Exception {
        SVFRMS app = quietApplication();
        for (int i = 0; i < 5; i++) app.addControlRequest("CH1", DeviceType.WATER_PUMP);
        FutureTask<Void> send = new FutureTask<>(() -> {
            app.processSensor("CH1", "Tomato", "Soil", 30);
            return null;
        });
        Thread producer = new Thread(send, "queue-capacity-test-producer");
        Thread consumer = new Thread(app.new ControlTask());
        producer.setDaemon(true);
        consumer.setDaemon(true);
        ExecutorService inspector = Executors.newSingleThreadExecutor();
        producer.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (producer.isAlive() && producer.getState() != Thread.State.WAITING
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertEquals(Thread.State.WAITING, producer.getState(), "producer must reach the full-queue wait");
            var snapshot = inspector.submit(app::snapshot).get(5, TimeUnit.SECONDS);
            assertEquals(1, snapshot.totalAlertsGenerated());
            assertEquals(50, snapshot.totalWaterUsage());
            assertEquals(0, snapshot.chamberSummaries().get("CH1").mitigationActions());
            assertEquals(5, app.pendingControls().size());
        } finally {
            consumer.start();
            try {
                send.get(5, TimeUnit.SECONDS);
                producer.join(5000);
                assertFalse(producer.isAlive());
            } finally {
                app.stopControlThread();
                consumer.join(5000);
                shutdown(inspector);
                assertFalse(consumer.isAlive());
            }
        }
        assertTrue(app.pendingControls().isEmpty());
        assertEquals(6, app.getChamberSummary("CH1").mitigationActions());
    }

    @Test
    void fourChambersRetainAllReadingsAndExpectedTotalsWithARealConsumer() throws Exception {
        Random samples = new Random(301) {
            @Override public int nextInt(int bound) {
                return switch (bound) {
                    case 30 -> 14; // 24 C: only Lettuce triggers, with no extra energy charge.
                    case 60 -> 30; // humidity 70%, soil 50%
                    case 900 -> 300; // 600 lux
                    case 80 -> 50; // nutrients 70%
                    default -> throw new AssertionError(bound);
                };
            }
        };
        SVFRMS app = new SVFRMS(samples, message -> {});
        Thread consumer = new Thread(app.new ControlTask());
        consumer.setDaemon(true);
        consumer.start();
        try {
            runTogether(List.of(
                () -> app.new ChamberTask("CH1", "Tomato").run(),
                () -> app.new ChamberTask("CH2", "Strawberry").run(),
                () -> app.new ChamberTask("CH3", "Lettuce").run(),
                () -> app.new ChamberTask("CH4", "Chili").run()));
        } finally {
            app.stopControlThread();
            consumer.join(5000);
            assertFalse(consumer.isAlive());
        }
        var snapshot = app.snapshot();
        assertEquals(400, snapshot.sensorLogs().size());
        assertEquals(160, snapshot.totalWaterUsage());
        assertEquals(80, snapshot.totalEnergyUsage());
        assertEquals(20, snapshot.totalAlertsGenerated());
        assertEquals(20, snapshot.chamberSummaries().get("CH3").mitigationActions());
        assertEquals(4, snapshot.chamberStates().size());
        assertTrue(app.pendingControls().isEmpty());
        assertAlertInvariants(snapshot);
        app.archiveAlerts();
        assertEquals(0, app.snapshot().totalAlerts());
        assertEquals(20, app.snapshot().archivedAlerts().size());
    }

    private static SVFRMS quietApplication() {
        return new SVFRMS(new Random(301), message -> {});
    }

    private static void assertAlertInvariants(FarmState.Snapshot snapshot) {
        assertEquals(snapshot.totalAlertsGenerated(), snapshot.totalAlerts() + snapshot.archivedAlerts().size());
        assertEquals(snapshot.totalAlertsGenerated(), snapshot.chamberSummaries().values().stream()
            .mapToInt(FarmState.SummarySnapshot::alerts).sum());
    }

    private static void runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Runnable task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    task.run();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            shutdown(pool);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "coordination timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    private static void shutdown(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "test workers must terminate");
    }
}
