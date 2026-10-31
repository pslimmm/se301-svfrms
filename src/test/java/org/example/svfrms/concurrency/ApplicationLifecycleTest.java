package org.example.svfrms.concurrency;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class ApplicationLifecycleTest {
    @TempDir Path directory;

    @Test
    void selfInterruptedProducerFailsRunInsteadOfPrintingNormalCompletion() {
        var output = new CopyOnWriteArrayList<String>();
        var context = context(output);
        context.telemetry().subscribe(reading -> Thread.currentThread().interrupt());
        try {
            assertThatThrownBy(() -> context.runSimulation(20)).isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(output).doesNotContain("System finished sensor processing.");
            assertThat(java.nio.file.Files.exists(directory.resolve("report.txt"))).isFalse();
            assertThat(context.actuatorService().isTerminated()).isTrue();
        } finally { context.close(); }
    }

    @Test
    void interruptionDoesNotHideCleanupFailureOrCacheFalseSuccess() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new AtomicBoolean();
        var original = new IllegalArgumentException("actuator cleanup original");
        var context = new SvfrmsApplicationContext(directory.resolve("report.txt"), Clock.systemUTC(), line -> {
            if (line.contains("[CONTROL]") && once.compareAndSet(false, true)) {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw original;
            }
        }, 42, Duration.ofSeconds(2));
        var failure = new AtomicReference<Throwable>();
        var restored = new AtomicBoolean();
        Thread closer = null;
        try {
            context.addControlRequest("CH1", "Water Pump");
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            closer = Thread.ofVirtual().start(() -> {
                try { context.close(); } catch (Throwable e) { failure.set(e); }
                finally { restored.set(Thread.currentThread().isInterrupted()); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (closer.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.yield();
            assertThat(closer.getState()).isEqualTo(Thread.State.TIMED_WAITING);
            closer.interrupt();
            while (closer.isInterrupted() && System.nanoTime() < deadline) Thread.yield();
            assertThat(closer.isInterrupted()).isFalse();
            release.countDown();
            closer.join(2500);
            assertThat(closer.isAlive()).isFalse();
            assertThat(failure.get()).hasRootCause(original);
            assertThat(restored).isTrue();
            assertThatThrownBy(context::close).hasRootCause(original);
        } finally {
            release.countDown();
            if (closer != null) { closer.interrupt(); closer.join(2500); }
            try { context.close(); } catch (IllegalStateException expectedFailure) { }
        }
    }

    @Test
    void concurrentCloseWaitsForTheSameCompletedCleanup() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new AtomicBoolean();
        var context = new SvfrmsApplicationContext(directory.resolve("report.txt"), Clock.systemUTC(), line -> {
            if (line.contains("[CONTROL]") && once.compareAndSet(false, true)) {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, 42, Duration.ofSeconds(1));
        var executor = Executors.newFixedThreadPool(2);
        try {
            context.addControlRequest("CH1", "Water Pump");
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var first = executor.submit(context::close);
            var closing = SvfrmsApplicationContext.class.getDeclaredField("closing");
            closing.setAccessible(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (!closing.getBoolean(context) && System.nanoTime() < deadline) Thread.yield();
            assertThat(closing.getBoolean(context)).isTrue();
            var second = executor.submit(context::close);
            assertThatThrownBy(() -> second.get(30, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(context.actuatorService().isTerminated()).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
            context.close();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void producerFailureAndInterruptionAlwaysCleanUp() throws Exception {
        var output = new CopyOnWriteArrayList<String>();
        var context = context(output);
        var firstProducerBlocked = new CountDownLatch(1);
        var siblingInterrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        context.telemetry().subscribe(reading -> {
            if (reading.chamberId().value().equals("CH1")) {
                firstProducerBlocked.countDown();
                try { release.await(); }
                catch (InterruptedException e) { siblingInterrupted.countDown(); Thread.currentThread().interrupt(); }
            } else if (reading.chamberId().value().equals("CH2")) {
                try { firstProducerBlocked.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw new IllegalArgumentException("required observer failed");
            }
        });
        try {
            var run = executor.submit(() -> { context.runSimulation(100); return null; });
            assertThatThrownBy(() -> run.get(2,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasRootCauseMessage("required observer failed");
            assertThat(siblingInterrupted.await(1,TimeUnit.SECONDS)).isTrue();
            assertThat(context.actuatorService().isTerminated()).isTrue();
            assertThat(output).doesNotContain("System finished sensor processing.");
            assertThat(java.nio.file.Files.exists(directory.resolve("report.txt"))).isFalse();
        } finally { release.countDown(); executor.shutdownNow(); context.close(); assertThat(executor.awaitTermination(2,TimeUnit.SECONDS)).isTrue(); }

        var interruptedOutput = new CopyOnWriteArrayList<String>();
        var interruptedContext = context(interruptedOutput);
        var entered = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var restored = new AtomicBoolean();
        interruptedContext.telemetry().subscribe(reading -> {
            entered.countDown();
            try { unblock.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        var caller = Thread.ofVirtual().start(() -> {
            try { interruptedContext.runSimulation(100); }
            catch (Throwable e) { failure.set(e); restored.set(Thread.currentThread().isInterrupted()); }
        });
        try {
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            caller.interrupt(); caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(InterruptedException.class);
            assertThat(restored).isTrue();
            assertThat(interruptedContext.actuatorService().isTerminated()).isTrue();
            assertThat(interruptedOutput).doesNotContain("System finished sensor processing.");
        } finally { unblock.countDown(); caller.interrupt(); caller.join(2000); interruptedContext.close(); }
    }

    @Test
    void externallyClosedRunCannotClaimNormalCompletion() throws Exception {
        var output = new CopyOnWriteArrayList<String>();
        var context = context(output);
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        context.telemetry().subscribe(reading -> {
            if (reading.sensorType() != org.example.svfrms.domain.sensor.SensorType.NUTRIENT) return;
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            var run = executor.submit(() -> { context.runSimulation(1); return null; });
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            context.close();
            assertThatThrownBy(() -> run.get(2,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(output).doesNotContain("System finished sensor processing.");
        } finally { release.countDown(); executor.shutdownNow(); context.close(); executor.awaitTermination(2,TimeUnit.SECONDS); }
    }

    @Test
    void closeBeforeStartIsTerminated() {
        var context = context(new CopyOnWriteArrayList<>());
        context.close(); context.close();
        assertThat(context.actuatorService().isTerminated()).isTrue();
    }

    @Test
    void commandFailurePreventsApplicationSuccessEvenWhenOutputFails() {
        var output = new CopyOnWriteArrayList<String>();
        var original = new IllegalArgumentException("control output failed");
        var context = new SvfrmsApplicationContext(directory.resolve("report.txt"),Clock.systemUTC(),line -> {
            output.add(line);
            if (line.contains("[CONTROL]") || line.startsWith("Actuator command failed:")) throw original;
        },42,Duration.ofSeconds(1));
        try {
            assertThatThrownBy(() -> context.runSimulation(20)).isInstanceOf(IllegalStateException.class);
            assertThat(context.actuatorService().failure()).containsSame(original);
            assertThat(context.actuatorService().isTerminated()).isTrue();
            assertThat(output).doesNotContain("System finished sensor processing.");
        } finally { try { context.close(); } catch (IllegalStateException expected) { } }
    }

    private SvfrmsApplicationContext context(CopyOnWriteArrayList<String> output) {
        return new SvfrmsApplicationContext(directory.resolve("report.txt"),Clock.systemUTC(),output::add,42,Duration.ofSeconds(1));
    }

    @Test
    void completionPrecedesSaveAndStorageFailurePreservesLegacyMessage() throws Exception {
        var output = new CopyOnWriteArrayList<String>();
        var context = new SvfrmsApplicationContext(directory,Clock.systemUTC(),output::add,42);
        try {
            context.runSimulation(0); // A directory cannot be overwritten as a report file.
            assertThat(output).contains("System finished sensor processing.");
            assertThat(output).anyMatch(s -> s.startsWith("Could not save archive report: "));
            assertThat(output).noneMatch(s -> s.startsWith("Archive report saved to: "));
        } finally { context.close(); }
    }

    @Test
    void retainedContextAllowsReadingsUntilQueuedMitigationIsNeeded() throws Exception {
        var context = new SvfrmsApplicationContext(directory.resolve("report.txt"),Clock.systemUTC(),s -> {},42);
        context.runSimulation(0);
        context.close();
        context.processSensor("CH1","Tomato","Temperature",21);
        assertThat(context.sensorLogs()).hasSize(1);
        assertThat(context.getChamberState("CH1").temperature()).isEqualTo(21);
        assertThat(context.resources().energyUsage()).isEqualTo(15);
        assertThatThrownBy(() -> context.processSensor("CH1","Tomato","Temperature",29)).isInstanceOf(IllegalStateException.class);
        assertThat(context.sensorLogs()).hasSize(2);
        assertThat(context.alerts().generatedAlertCount()).isEqualTo(2);
        assertThat(context.resources().energyUsage()).isEqualTo(25);
    }
}
