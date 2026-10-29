package org.example.svfrms.concurrency;

import org.example.SVFRMS;
import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** Exercises the actual static facade, including its class monitor and registered JVM hook. */
@Timeout(10)
class FacadeShutdownTest {
    @TempDir Path directory;

    @Test
    void terminatedFailedSessionsCanBeRetiredForAFreshMain() throws Exception {
        SVFRMS.closeSession();
        PrintStream previous = System.out;
        try (var captured = new PrintStream(OutputStream.nullOutputStream())) {
            System.setOut(captured);
            for (boolean explicitFacadeClose : new boolean[]{false, true}) {
                var failed = new SvfrmsApplicationContext(directory.resolve("failed.txt"), Clock.systemUTC(), line -> {
                    if (line.contains("[CONTROL]")) throw new IllegalArgumentException("previous session failure");
                }, 42);
                installSession(failed);
                SVFRMS.addControlRequest("CH1", "Cooling Fan");
                assertThatThrownBy(() -> failed.awaitControls(Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class);
                if (explicitFacadeClose) assertThatThrownBy(SVFRMS::closeSession).isInstanceOf(IllegalStateException.class);
                else assertThatThrownBy(failed::close).isInstanceOf(IllegalStateException.class);
                assertThat(failed.actuatorService().isTerminated()).isTrue();
                assertThatCode(() -> SVFRMS.main(new String[0])).doesNotThrowAnyException();
                Field field = SVFRMS.class.getDeclaredField("session");
                field.setAccessible(true);
                var fresh = (SvfrmsApplicationContext) field.get(null);
                assertThat(fresh).isNotSameAs(failed);
                assertThat(fresh.sensorLogs()).hasSize(400);
                SVFRMS.closeSession();
            }
        } finally {
            try { SVFRMS.closeSession(); } catch (IllegalStateException expectedOnRed) { }
            installSession(null);
            System.setOut(previous);
        }
    }

    @Test
    void closeDoesNotHoldFacadeMonitorWhileWorkerCallbackReenters() throws Exception {
        SVFRMS.closeSession();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        var observedTemperature = new AtomicReference<Double>();
        var context = new SvfrmsApplicationContext(directory.resolve("report.txt"), Clock.systemUTC(), line -> {
            if (line.contains("[CONTROL]") && once.compareAndSet(false, true)) {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                observedTemperature.set(SVFRMS.getChamberState("CH1").temperature());
            }
        }, 42, Duration.ofSeconds(1));
        installSession(context);
        Thread closer = null;
        try {
            SVFRMS.addControlRequest("CH1", "Cooling Fan");
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            closer = Thread.ofVirtual().start(() -> {
                try { SVFRMS.closeSession(); } catch (Throwable e) { failure.set(e); }
            });
            Field closing = SvfrmsApplicationContext.class.getDeclaredField("closing");
            closing.setAccessible(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!closing.getBoolean(context) && System.nanoTime() < deadline) Thread.yield();
            assertThat(closing.getBoolean(context)).isTrue();
            release.countDown();
            closer.join(2500);
            assertThat(closer.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
            assertThat(observedTemperature.get()).isEqualTo(19.0);
            assertThat(context.actuatorService().isTerminated()).isTrue();
        } finally {
            release.countDown();
            if (closer != null) { closer.interrupt(); closer.join(2500); }
            try { context.close(); } catch (IllegalStateException expectedOnRed) { }
            installSession(null);
        }
    }

    @Test
    void mainDoesNotReplaceProducerFailureWithCleanupFailure() throws Exception {
        SVFRMS.closeSession();
        PrintStream previous = System.out;
        var controlEntered = new CountDownLatch(1);
        var producerFailed = new CountDownLatch(1);
        var releaseControl = new CountDownLatch(1);
        var firstLettuce = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        var producerError = new IllegalArgumentException("producer output failed");
        var controlError = new IllegalArgumentException("control output failed");
        PrintStream output = new PrintStream(OutputStream.nullOutputStream()) {
            @Override public void println(String line) {
                try {
                    if (line.contains("[CONTROL]")) {
                        controlEntered.countDown();
                        if (!releaseControl.await(3, TimeUnit.SECONDS)) throw new AssertionError("Test did not release control");
                        throw controlError;
                    }
                    if (line.contains("[ALERT]")) {
                        // The fixed legacy simulation seed starts CH3 with high temperature.
                        if (line.equals("[CH3][Lettuce][ALERT] CH3 Lettuce High Temp") && firstLettuce.compareAndSet(false, true)) return;
                        if (!controlEntered.await(3, TimeUnit.SECONDS)) throw new AssertionError("Control never started");
                        producerFailed.countDown();
                        throw producerError;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw producerError;
                }
            }
        };
        Thread main = null;
        System.setOut(output);
        try {
            main = Thread.ofVirtual().start(() -> {
                try { SVFRMS.main(new String[0]); } catch (Throwable e) { failure.set(e); }
            });
            assertThat(controlEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(producerFailed.await(3, TimeUnit.SECONDS)).isTrue();
            releaseControl.countDown();
            main.join(3000);
            assertThat(main.isAlive()).isFalse();
            assertThat(failure.get()).hasRootCause(producerError);
            assertThat(failure.get().getSuppressed()).isNotEmpty();
        } finally {
            releaseControl.countDown();
            if (main != null) { main.interrupt(); main.join(3000); }
            try { SVFRMS.closeSession(); } catch (IllegalStateException expectedFailure) { }
            installSession(null);
            System.setOut(previous);
            output.close();
        }
    }

    @Test
    void actualJvmShutdownHookClosesTheFacadeWorker() throws Exception {
        Path marker = directory.resolve("closed.txt");
        Path ready = directory.resolve("ready.txt");
        Path log = directory.resolve("child.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, ShutdownHookProbe.class.getName(), marker.toString(), ready.toString())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.yield();
            assertThat(Files.exists(ready)).as(Files.readString(log)).isTrue();
            child.getOutputStream().write(42);
            child.getOutputStream().flush();
            assertThat(child.waitFor(4, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).as(Files.readString(log)).isZero();
            assertThat(Files.exists(marker)).as(Files.readString(log)).isTrue();
            assertThat(Files.readString(marker)).isEqualTo("closed");
            assertThat(Files.readString(log)).doesNotContain("System finished sensor processing.");
        } finally {
            if (child.isAlive()) child.destroyForcibly();
            assertThat(child.waitFor(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    static void installSession(SvfrmsApplicationContext context) throws Exception {
        Field field = SVFRMS.class.getDeclaredField("session");
        field.setAccessible(true);
        synchronized (SVFRMS.class) { field.set(null, context); }
    }
}
