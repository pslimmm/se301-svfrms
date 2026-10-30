package org.example.svfrms.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.example.svfrms.alert.AlertArchiver;
import org.example.svfrms.alert.AlertService;
import org.example.svfrms.alert.AlertSnapshot;
import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.chamber.ChamberStateStore;
import org.example.svfrms.chamber.ChamberSummary;
import org.example.svfrms.metrics.ResourceUsageTracker;
import org.example.svfrms.model.Alert;
import org.example.svfrms.model.AlertSeverity;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.SensorType;
import org.example.svfrms.report.ReportLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Stress tests that hammer each shared component from many threads at once. Every one of these
 * failed (or could fail) with the legacy design. With the fixes the results are exact every time.
 */
class ThreadSafetyStressTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS = 5_000;

    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    /** C2: legacy "totalWaterUsage += 2" from many threads lost updates. */
    @Test
    void resourceTotalsNeverLoseUpdates() throws Exception {
        ResourceUsageTracker usage = new ResourceUsageTracker();
        runConcurrently(usage::recordChamberCycle);
        assertEquals(2L * THREADS * ITERATIONS, usage.water());
        assertEquals((long) THREADS * ITERATIONS, usage.energy());
    }

    /** C2 + C3: legacy totalAlerts++ and alerts.add(...) on an ArrayList. */
    @Test
    void everyRaisedAlertIsCountedAndStored() throws Exception {
        AlertService alerts = new AlertService();
        runConcurrently(() -> alerts.raise(sampleAlert("CH1")));
        AlertSnapshot snapshot = alerts.snapshot();
        assertEquals((long) THREADS * ITERATIONS, snapshot.totalGenerated());
        assertEquals(THREADS * ITERATIONS, snapshot.active().size());
    }

    /** C1: legacy getChamberState() check-then-act on a HashMap could create two states (or return null). */
    @Test
    void eachChamberGetsExactlyOneState() throws Exception {
        for (int round = 0; round < 200; round++) {
            ChamberStateStore store = new ChamberStateStore();
            Set<ChamberState> seen = ConcurrentHashMap.newKeySet();
            CyclicBarrier startTogether = new CyclicBarrier(THREADS);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    seen.add(store.stateOf("CH1"));
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
            assertEquals(1, seen.size(), "two threads created different states for the same chamber");
        }
    }

    /** C5: chamber thread and device thread both doing read-modify-write on the same field. */
    @Test
    void chamberStateAdjustIsAtomic() throws Exception {
        ChamberState state = new ChamberState();
        state.set(SensorType.NUTRIENT, 0);
        runConcurrently(() -> state.adjust(SensorType.NUTRIENT, v -> v + 1));
        assertEquals((double) THREADS * ITERATIONS, state.get(SensorType.NUTRIENT));
    }

    /** C4: summary counters were incremented by chamber and device threads with no lock. */
    @Test
    void chamberSummaryCountsAreExact() throws Exception {
        ChamberSummary summary = new ChamberSummary();
        runConcurrently(() -> {
            summary.recordAlert("alert");
            summary.recordAction("action");
        });
        assertEquals(THREADS * ITERATIONS, summary.snapshot().alerts());
        assertEquals(THREADS * ITERATIONS, summary.snapshot().mitigationActions());
    }

    /**
     * C6 + C7: archiving, reporting and raising alerts all at once. The legacy code nested two locks here.
     * The new design never holds two locks together, so this must finish (no deadlock) and the numbers
     * must always add up (no torn reads).
     */
    @Test
    void archivingWhileRaisingAndReportingNeverDeadlocksAndStaysConsistent() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            AlertService alerts = new AlertService();
            ReportLog reportLog = new ReportLog();
            AlertArchiver archiver = new AlertArchiver(alerts, reportLog, new PrintStream(OutputStream.nullOutputStream()));
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int role = t % 3;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 2_000; i++) {
                        switch (role) {
                            case 0 -> alerts.raise(sampleAlert("CH" + i % 4));
                            case 1 -> archiver.archive();
                            default -> {
                                AlertSnapshot s = alerts.snapshot();
                                assertEquals(s.totalGenerated(), s.active().size() + s.archived().size());
                                reportLog.entries();
                            }
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            AlertSnapshot end = alerts.snapshot();
            assertEquals(end.totalGenerated(), end.active().size() + end.archived().size());
        });
    }

    private void runConcurrently(Runnable action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    action.run();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
    }

    static Alert sampleAlert(String chamber) {
        return new Alert(chamber, "Tomato", SensorType.TEMPERATURE, Breach.HIGH, AlertSeverity.WARNING,
                chamber + " Tomato High Temp");
    }
}
