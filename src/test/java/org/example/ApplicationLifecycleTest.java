package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.example.ControlQueueTest.TestWorker;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class ApplicationLifecycleTest {
    @TempDir Path directory;

    @Test
    void successfulRunDrainsReportsTerminatesAndRejectsRestartAndLateWork() throws Exception {
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        Path report = directory.resolve("archive/daily_report.txt");
        SVFRMS app = new SVFRMS(scriptedSamples(), output::add, report);
        app.run();
        assertEquals(SVFRMS.RunState.COMPLETED, app.runState());
        assertTrue(app.workersTerminated());
        assertTrue(app.pendingControls().isEmpty());
        var snapshot = app.snapshot();
        assertEquals(400, snapshot.sensorLogs().size());
        assertEquals(160, snapshot.totalWaterUsage());
        assertEquals(80, snapshot.totalEnergyUsage());
        assertEquals(20, snapshot.totalAlertsGenerated());
        assertEquals(20, snapshot.chamberSummaries().get("CH3").mitigationActions());
        assertEquals(0, snapshot.totalAlerts());
        assertEquals(20, snapshot.archivedAlerts().size());
        assertTrue(Files.readString(report).contains("Generated Alerts: 20"));
        assertTrue(output.contains("System finished sensor processing."));
        assertThrows(IllegalStateException.class, app::run);
        assertThrows(RejectedExecutionException.class, () -> app.addControlRequest("late", DeviceType.WATER_PUMP));
        assertThrows(RejectedExecutionException.class, () -> app.processSensor("late", "Tomato", "Temperature", 25));
        assertEquals(snapshot, app.snapshot(), "rejected work must not change the completed result");
    }

    @Test
    void concurrentRunInvocationIsRejectedWithoutCancellingTheFirst() throws Exception {
        CountDownLatch sampling = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Random random = gatedSamples(sampling, release);
        SVFRMS app = new SVFRMS(random, message -> {}, directory.resolve("report.txt"));
        try (TestWorker<Void> runner = new TestWorker<>(() -> { app.run(); return null; })) {
            assertTrue(sampling.await(5, TimeUnit.SECONDS));
            assertEquals(SVFRMS.RunState.RUNNING, app.runState());
            assertThrows(IllegalStateException.class, app::run);
            assertEquals(SVFRMS.RunState.RUNNING, app.runState());
            release.countDown();
            runner.result();
        } finally {
            release.countDown();
        }
        assertEquals(SVFRMS.RunState.COMPLETED, app.runState());
        assertTrue(app.workersTerminated());
    }

    @Test
    void producerFailureCancelsPeersAndDoesNotOverwriteAnExistingReport() throws Exception {
        IllegalStateException injected = new IllegalStateException("sensor source failed");
        Random random = new Random(301) {
            @Override public int nextInt(int bound) { throw injected; }
        };
        Path report = directory.resolve("report.txt");
        Files.writeString(report, "previous completed run");
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        SVFRMS app = new SVFRMS(random, output::add, report);
        IllegalStateException failure = assertThrows(IllegalStateException.class, app::run);
        assertSame(injected, failure.getCause());
        assertFailedWithoutCompletion(app, output);
        assertTrue(app.snapshot().reports().isEmpty());
        assertEquals("previous completed run", Files.readString(report));
        assertThrows(IllegalStateException.class, app::run);
        assertThrows(RejectedExecutionException.class, () -> app.addControlRequest("late", DeviceType.WATER_PUMP));
    }

    @Test
    void consumerFailureReleasesBlockedSubmittersAndRetainsItsOriginalCause() throws Exception {
        CountDownLatch controlEntered = new CountDownLatch(1);
        CountDownLatch failControl = new CountDownLatch(1);
        IllegalStateException injected = new IllegalStateException("control output failed");
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        SVFRMS app = new SVFRMS(scriptedSamples(), message -> {
            if (message.contains("[CONTROL]")) {
                controlEntered.countDown();
                await(failControl);
                throw injected;
            }
            output.add(message);
        }, directory.resolve("report.txt"));
        for (int i = 0; i < 5; i++) app.addControlRequest("queued", DeviceType.WATER_PUMP);
        try (TestWorker<Boolean> sender1 = submitOne(app);
             TestWorker<Boolean> sender2 = submitOne(app)) {
            sender1.awaitState(Thread.State.WAITING);
            sender2.awaitState(Thread.State.WAITING);
            try (TestWorker<Void> runner = new TestWorker<>(() -> { app.run(); return null; })) {
                assertTrue(controlEntered.await(5, TimeUnit.SECONDS));
                // Only one queued request has been removed; at least one sender still faces backpressure.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (app.pendingControls().size() < 5 && System.nanoTime() < deadline) Thread.yield();
                assertEquals(5, app.pendingControls().size());
                failControl.countDown();
                Throwable failure = assertThrows(ExecutionException.class, runner::result).getCause();
                assertInstanceOf(IllegalStateException.class, failure);
                assertSame(injected, failure.getCause());
                boolean cancelled1 = sender1.result();
                boolean cancelled2 = sender2.result();
                assertTrue(cancelled1 || cancelled2, "abort must release blocked senders");
            } finally {
                failControl.countDown();
            }
        }
        assertFailedWithoutCompletion(app, output);
        assertFalse(Files.exists(directory.resolve("report.txt")));
        assertTrue(app.snapshot().reports().isEmpty());
    }

    @Test
    void interruptedFullQueueSubmissionRestoresInterruptAndRetainsDetectionAccounting() throws Exception {
        SVFRMS app = new SVFRMS(scriptedSamples(), message -> {});
        for (int i = 0; i < 5; i++) app.addControlRequest("CH1", DeviceType.WATER_PUMP);
        try (TestWorker<Boolean> sender = new TestWorker<>(() -> {
            try {
                app.processSensor("CH1", "Tomato", "Soil", 30);
                return false;
            } catch (CancellationException e) {
                return Thread.currentThread().isInterrupted();
            }
        })) {
            sender.awaitState(Thread.State.WAITING);
            sender.thread.interrupt();
            assertTrue(sender.result());
        }
        assertEquals(1, app.snapshot().totalAlertsGenerated());
        assertEquals(50, app.snapshot().totalWaterUsage());
        assertEquals(0, app.getChamberSummary("CH1").mitigationActions());
        assertEquals(5, app.pendingControls().size());
        app.stopControlThread();
        app.new ControlTask().run();
        assertEquals(5, app.getChamberSummary("CH1").mitigationActions());
    }

    @Test
    void interruptedIdleConsumerTerminatesAndClosesAdmission() throws Exception {
        SVFRMS app = new SVFRMS(scriptedSamples(), message -> {});
        try (TestWorker<Boolean> consumer = new TestWorker<>(() -> {
            try {
                app.new ControlTask().run();
                return false;
            } catch (CancellationException e) {
                return Thread.currentThread().isInterrupted();
            }
        })) {
            consumer.awaitState(Thread.State.TIMED_WAITING);
            consumer.thread.interrupt();
            assertTrue(consumer.result());
            assertThrows(RejectedExecutionException.class, () -> app.addControlRequest("late", DeviceType.WATER_PUMP));
        }
    }

    @Test
    void interruptingRunCancelsWorkersAndPreservesCallerInterruptStatus() throws Exception {
        CountDownLatch sampling = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        SVFRMS app = new SVFRMS(gatedSamples(sampling, release), output::add, directory.resolve("report.txt"));
        try (TestWorker<Boolean> runner = new TestWorker<>(() -> {
            try {
                app.run();
                return false;
            } catch (InterruptedException e) {
                return Thread.currentThread().isInterrupted();
            }
        })) {
            assertTrue(sampling.await(5, TimeUnit.SECONDS));
            runner.thread.interrupt();
            assertTrue(runner.result());
        } finally {
            release.countDown();
        }
        assertEquals(SVFRMS.RunState.CANCELLED, app.runState());
        assertTrue(app.workersTerminated());
        assertFalse(output.contains("System finished sensor processing."));
        assertFalse(Files.exists(directory.resolve("report.txt")));
    }

    @Test
    void preInterruptedRunDoesNotStartWorkers() throws Exception {
        SVFRMS app = new SVFRMS(scriptedSamples(), message -> {}, directory.resolve("report.txt"));
        try (TestWorker<Boolean> runner = new TestWorker<>(() -> {
            Thread.currentThread().interrupt();
            try {
                app.run();
                return false;
            } catch (InterruptedException e) {
                return Thread.currentThread().isInterrupted();
            }
        })) {
            assertTrue(runner.result());
        }
        assertEquals(SVFRMS.RunState.CANCELLED, app.runState());
        assertTrue(app.workersTerminated());
        assertTrue(app.snapshot().sensorLogs().isEmpty());
    }

    @Test
    void closedApplicationCannotStartWorkers() {
        SVFRMS app = new SVFRMS(scriptedSamples(), message -> {}, directory.resolve("report.txt"));
        app.stopControlThread();
        assertThrows(RejectedExecutionException.class, app::run);
        assertEquals(SVFRMS.RunState.FAILED, app.runState());
        assertTrue(app.workersTerminated());
        assertTrue(app.snapshot().sensorLogs().isEmpty());
        assertFalse(Files.exists(directory.resolve("report.txt")));
    }

    @Test
    void uncooperativeCallbackProducesABoundedTerminationDiagnostic() throws Exception {
        CountDownLatch controlEntered = new CountDownLatch(1);
        CountDownLatch releaseControl = new CountDownLatch(1);
        IllegalStateException injected = new IllegalStateException("producer failed while control blocked");
        Random random = new Random(301) {
            @Override public int nextInt(int bound) {
                await(controlEntered);
                throw injected;
            }
        };
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        SVFRMS app = new SVFRMS(random, message -> {
            if (message.contains("[CONTROL]")) {
                assertTrue(Thread.currentThread().isDaemon(), "abandoned callbacks must not pin the CLI JVM");
                controlEntered.countDown();
                boolean interrupted = false;
                while (true) {
                    try {
                        releaseControl.await();
                        break;
                    } catch (InterruptedException ignored) {
                        interrupted = true; // Deliberately uncooperative fixture, released in finally below.
                    }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            output.add(message);
        }, directory.resolve("report.txt"));
        app.addControlRequest("CH1", DeviceType.WATER_PUMP);
        try (TestWorker<Void> runner = new TestWorker<>(() -> { app.run(); return null; })) {
            Throwable failure = assertThrows(ExecutionException.class, runner::result).getCause();
            assertSame(injected, failure.getCause());
            assertEquals(1, failure.getSuppressed().length);
            assertTrue(failure.getSuppressed()[0].getMessage().contains("did not terminate within 5 seconds"));
            assertEquals(SVFRMS.RunState.FAILED, app.runState());
            assertFalse(app.workersTerminated());
            assertFalse(output.contains("System finished sensor processing."));
            assertFalse(Files.exists(directory.resolve("report.txt")));
        } finally {
            releaseControl.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!app.workersTerminated() && System.nanoTime() < deadline) Thread.yield();
            assertTrue(app.workersTerminated(), "fixture must release its deliberately blocked callback");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"===== DAILY REPORT =====", "Archiving alerts..."})
    void reportingWorkerFailurePreventsSuccessAndFinalFile(String failingPrefix) {
        IllegalStateException injected = new IllegalStateException("reporting failed");
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        SVFRMS app = new SVFRMS(scriptedSamples(), message -> {
            if (message.startsWith(failingPrefix)) throw injected;
            output.add(message);
        }, directory.resolve("report.txt"));
        IllegalStateException failure = assertThrows(IllegalStateException.class, app::run);
        assertSame(injected, failure.getCause());
        assertFailedWithoutCompletion(app, output);
        assertFalse(Files.exists(directory.resolve("report.txt")));
    }

    private static void assertFailedWithoutCompletion(SVFRMS app, ConcurrentLinkedQueue<String> output) {
        assertEquals(SVFRMS.RunState.FAILED, app.runState());
        assertTrue(app.workersTerminated());
        assertFalse(output.contains("System finished sensor processing."));
        assertTrue(output.stream().noneMatch(line -> line.startsWith("Archive report saved to:")));
    }

    private static TestWorker<Boolean> submitOne(SVFRMS app) {
        return new TestWorker<>(() -> {
            try {
                app.addControlRequest("external", DeviceType.GROW_LIGHT);
                return false;
            } catch (CancellationException e) {
                assertTrue(Thread.currentThread().isInterrupted());
                return true;
            }
        });
    }

    private static Random scriptedSamples() {
        return new Random(301) {
            @Override public int nextInt(int bound) {
                return switch (bound) {
                    case 30 -> 14;
                    case 60 -> 30;
                    case 900 -> 300;
                    case 80 -> 50;
                    default -> throw new AssertionError(bound);
                };
            }
        };
    }

    private static Random gatedSamples(CountDownLatch entered, CountDownLatch release) {
        Random source = scriptedSamples();
        AtomicBoolean first = new AtomicBoolean(true);
        return new Random(301) {
            @Override public int nextInt(int bound) {
                if (first.compareAndSet(true, false)) {
                    entered.countDown();
                    await(release);
                }
                return source.nextInt(bound);
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(8, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("fixture interrupted");
        }
    }
}
