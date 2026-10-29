package org.example.svfrms.concurrency;

import org.example.SVFRMS;
import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only process driver; no stdin exit command is added to the production application. */
public final class ShutdownHookProbe {
    private ShutdownHookProbe() { }

    public static void main(String[] args) throws Exception {
        SVFRMS.closeSession(); // Load the real facade and register its real application hook.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new AtomicBoolean();
        var context = new SvfrmsApplicationContext(Path.of("archive/report.txt"), Clock.systemUTC(), line -> {
            if (line.contains("[CONTROL]") && once.compareAndSet(false, true)) {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, 42);
        FacadeShutdownTest.installSession(context);
        SVFRMS.addControlRequest("CH1", "Water Pump");
        if (!entered.await(1, TimeUnit.SECONDS)) throw new AssertionError("Accepted control did not start");
        SVFRMS.addControlRequest("CH1", "Grow Light"); // Keep accepted work queued at shutdown.
        Field closing = SvfrmsApplicationContext.class.getDeclaredField("closing");
        closing.setAccessible(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                // Observe the production hook; this test hook must never perform its cleanup.
                while (!closing.getBoolean(context) && System.nanoTime() < deadline) Thread.yield();
                if (!closing.getBoolean(context)) throw new AssertionError("Production hook never closed session");
                release.countDown();
                while (!context.isTerminated() && System.nanoTime() < deadline) Thread.yield();
                if (!context.isTerminated()) throw new AssertionError("Worker still alive");
                if (context.getChamberSummary("CH1").mitigationActions() != 2
                        || context.getChamberState("CH1").soilMoisture() != 80
                        || context.getChamberState("CH1").lightLevel() != 750) {
                    throw new AssertionError("Accepted work was not drained");
                }
                Files.writeString(Path.of(args[0]), "closed");
            } catch (Exception e) { throw new IllegalStateException(e); }
            finally { release.countDown(); }
        }, "test-shutdown-observer"));
        Files.writeString(Path.of(args[1]), "ready");
        if (System.in.read() != 42) throw new AssertionError("Missing shutdown command");
        System.exit(0);
    }
}
