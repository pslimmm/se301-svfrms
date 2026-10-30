package org.example.svfrms.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.chamber.ChamberStateStore;
import org.example.svfrms.device.Device;
import org.example.svfrms.device.DeviceController;
import org.example.svfrms.device.DeviceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests for the device command queue: liveness (C8) and orderly shutdown (C10). */
class QueuedControlDispatcherTest {

    private final AtomicInteger activations = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        worker.shutdownNow();
    }

    /** Legacy addControlRequest() swallowed the interrupt and kept waiting forever on a full queue. */
    @Test
    void producerBlockedOnFullQueueCanBeInterrupted() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            QueuedControlDispatcher dispatcher = new QueuedControlDispatcher(controller(), 1);
            dispatcher.request("CH1", "Counter"); // fills the queue; no worker started
            AtomicBoolean sawInterrupt = new AtomicBoolean();
            Thread producer = new Thread(() -> {
                dispatcher.request("CH1", "Counter"); // blocks here
                sawInterrupt.set(Thread.currentThread().isInterrupted());
            });
            producer.start();
            waitUntilBlocked(producer);

            producer.interrupt();
            producer.join(2_000);

            assertFalse(producer.isAlive(), "producer should stop when interrupted");
            assertTrue(sawInterrupt.get(), "interrupt flag must be restored, not swallowed");
        });
    }

    /** Legacy: an exception in activateControl() killed the ControlThread and producers then hung. */
    @Test
    void workerKeepsRunningAfterAFailingCommand() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            QueuedControlDispatcher dispatcher = new QueuedControlDispatcher(controller(), 5);
            dispatcher.start(worker);
            dispatcher.request("CH1", "Broken");
            for (int i = 0; i < 20; i++) {
                dispatcher.request("CH1", "Counter");
            }
            dispatcher.stopAndDrain();
            worker.shutdown();
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
            assertEquals(20, activations.get());
        });
    }

    @Test
    void stopAndDrainRunsEveryCommandFromManyProducers() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            QueuedControlDispatcher dispatcher = new QueuedControlDispatcher(controller(), QueuedControlDispatcher.LEGACY_CAPACITY);
            dispatcher.start(worker);
            ExecutorService producers = Executors.newFixedThreadPool(4);
            for (int p = 0; p < 4; p++) {
                producers.submit(() -> {
                    for (int i = 0; i < 250; i++) {
                        dispatcher.request("CH1", "Counter");
                    }
                });
            }
            producers.shutdown();
            assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));
            dispatcher.stopAndDrain();
            worker.shutdown();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(1_000, activations.get());
            assertEquals(0, dispatcher.pending());
        });
    }

    private DeviceController controller() {
        DeviceRegistry registry = new DeviceRegistry();
        registry.register(new Device() {
            public String name() {
                return "Counter";
            }

            public String activate(ChamberState state) {
                activations.incrementAndGet();
                return "counted";
            }
        });
        registry.register(new Device() {
            public String name() {
                return "Broken";
            }

            public String activate(ChamberState state) {
                throw new IllegalStateException("simulated device fault");
            }
        });
        return new DeviceController(registry, new ChamberStateStore());
    }

    private static void waitUntilBlocked(Thread thread) throws InterruptedException {
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            Thread.sleep(5);
        }
    }
}
