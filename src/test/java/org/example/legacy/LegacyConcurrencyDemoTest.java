package org.example.legacy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates the original bugs in the untouched legacy code. Races are timing-dependent, so these
 * tests retry a few times and are <em>skipped</em> (not failed) if the race happens not to show up on
 * this machine. The interrupt test is deterministic. The matching "fixed" tests for the new code are
 * strict: see ThreadSafetyStressTest and QueuedControlDispatcherTest.
 */
@Tag("legacy-demo")
class LegacyConcurrencyDemoTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void reset() {
        LegacyHarness.reset();
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        LegacyHarness.reset();
    }

    /** C2: "totalWaterUsage += 100" is read-add-write, so concurrent calls overwrite each other. */
    @Test
    void legacyTotalsLoseUpdates() throws Exception {
        boolean lost = false;
        for (int attempt = 0; attempt < 10 && !lost; attempt++) {
            SVFRMS.totalWaterUsage = 0;
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 100_000; i++) {
                        SVFRMS.irrigationCycle();
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
            int expected = 8 * 100_000 * 100;
            lost = SVFRMS.totalWaterUsage != expected;
            if (lost) {
                System.out.println("[legacy-demo] lost updates: expected " + expected + " L but legacy counted " + SVFRMS.totalWaterUsage + " L");
            }
        }
        assumeTrue(lost, "race did not show up on this run");
    }

    /** C1: two threads both see "missing" in the HashMap and each create their own ChamberState. */
    @Test
    void legacyChamberLookupCanCreateDuplicateState() throws Exception {
        boolean duplicated = false;
        for (int round = 0; round < 2_000 && !duplicated; round++) {
            String chamber = "R" + round;
            Set<Object> seen = ConcurrentHashMap.newKeySet();
            AtomicBoolean gotNull = new AtomicBoolean(); // the NPE we saw in the very first legacy run
            CyclicBarrier together = new CyclicBarrier(8);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    together.await();
                    Object state = SVFRMS.getChamberState(chamber);
                    if (state == null) {
                        gotNull.set(true);
                    } else {
                        seen.add(state);
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
            duplicated = seen.size() > 1 || gotNull.get();
            if (duplicated) {
                System.out.println("[legacy-demo] chamber " + chamber + " got " + seen.size()
                        + " different states" + (gotNull.get() ? " and a null state" : ""));
            }
        }
        assumeTrue(duplicated, "race did not show up on this run");
    }

    /** C8: the legacy producer swallows InterruptedException, so it cannot be cancelled. */
    @Test
    void legacyBlockedProducerIgnoresInterrupt() throws Exception {
        for (int i = 0; i < SVFRMS.controlQueueSize; i++) {
            SVFRMS.controlQueue.add("CH1|Cooling Fan"); // queue full, and no ControlThread running
        }
        Thread producer = new Thread(() -> SVFRMS.addControlRequest("CH1", "Water Pump"));
        producer.start();
        while (producer.getState() != Thread.State.WAITING) {
            Thread.sleep(5);
        }

        producer.interrupt();
        producer.join(500);
        boolean stillStuck = producer.isAlive();
        System.out.println("[legacy-demo] interrupted legacy producer still blocked: " + stillStuck);

        synchronized (SVFRMS.controlQueue) { // release it so the test can finish
            SVFRMS.controlQueue.clear();
            SVFRMS.controlQueue.notifyAll();
        }
        producer.join(2_000);
        assertTrue(stillStuck, "legacy producer should ignore interrupt");
    }
}
