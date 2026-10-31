package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Repeated contention supplements the locking argument and deterministic failure tests. */
@Timeout(25)
class IntegrationStressTest {
    @TempDir Path directory;

    @RepeatedTest(5)
    void reportsAndArchivesRemainCoherentDuringFourProducerContention() throws Exception {
        FarmState state = new FarmState();
        CyclicBarrier phase = new CyclicBarrier(6);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> work = new ArrayList<>();
        try {
            for (int producer = 0; producer < 4; producer++) {
                int id = producer;
                work.add(pool.submit(() -> {
                    for (int batch = 0; batch < 20; batch++) {
                        phase.await(5, TimeUnit.SECONDS);
                        for (int item = 0; item < 10; item++) {
                            // Unique IDs make lost/duplicated alerts independently detectable.
                            state.processSensor("P" + id + "-" + batch + "-" + item,
                                "Tomato", "Temperature", 20);
                        }
                        phase.await(5, TimeUnit.SECONDS);
                    }
                    return null;
                }));
            }
            work.add(pool.submit(() -> {
                for (int batch = 0; batch < 20; batch++) {
                    phase.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < 10; i++) {
                        state.archiveAlerts();
                        assertCoherent(state.snapshot());
                    }
                    phase.await(5, TimeUnit.SECONDS);
                }
                return null;
            }));
            work.add(pool.submit(() -> {
                for (int batch = 0; batch < 20; batch++) {
                    phase.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < 10; i++) {
                        var report = state.dailyReportSnapshot();
                        assertCoherent(report);
                        assertEquals("Daily Report", report.reports().getLast());
                    }
                    phase.await(5, TimeUnit.SECONDS);
                }
                return null;
            }));
            for (Future<?> task : work) task.get(15, TimeUnit.SECONDS);
        } finally {
            work.forEach(task -> task.cancel(true));
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        state.archiveAlerts();
        var snapshot = state.snapshot();
        assertCoherent(snapshot);
        assertEquals(800, snapshot.totalAlertsGenerated());
        assertEquals(0, snapshot.totalAlerts());
        assertEquals(800, new HashSet<>(snapshot.archivedAlerts()).size());
        assertEquals(12000, snapshot.totalEnergyUsage());
        assertEquals(401, snapshot.reports().size());
    }

    @RepeatedTest(10)
    void highAlertRunsDrainEveryCommandAndRetainExactAccounting() throws Exception {
        SensorSource samples = (chamber, sensor) -> switch (sensor) {
            case TEMPERATURE -> 35;
            case HUMIDITY -> 40;
            case SOIL -> 20;
            case LIGHT -> 300;
            case NUTRIENT -> 20;
        };
        Path report = directory.resolve("archive/daily_report.txt");
        FarmApplication app = new FarmApplication(CropCatalog.legacyDefaults(), samples,
            Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC), message -> {},
            report, new FileArchiveWriter());
        app.run();
        var snapshot = app.snapshot();
        assertEquals(FarmApplication.RunState.COMPLETED, app.runState());
        assertTrue(app.workersTerminated());
        assertTrue(app.pendingControls().isEmpty());
        assertEquals(400, snapshot.sensorLogs().size());
        assertEquals(300, snapshot.totalAlertsGenerated());
        assertEquals(300, snapshot.archivedAlerts().size());
        assertEquals(0, snapshot.totalAlerts());
        assertEquals(2060, snapshot.totalWaterUsage());
        assertEquals(480, snapshot.totalEnergyUsage());
        for (var expected : java.util.Map.of("CH1", 100, "CH2", 80, "CH3", 60, "CH4", 60).entrySet()) {
            var summary = snapshot.chamberSummaries().get(expected.getKey());
            assertEquals(expected.getValue().intValue(), summary.alerts());
            assertEquals(summary.alerts(), summary.mitigationActions());
        }
        List<String> lines = Files.readAllLines(report);
        for (String line : List.of("Generated Alerts: 300", "Active Alerts: 0", "Water Usage: 2060 L",
                "Energy Usage: 480 kWh", "Archived alerts: 300")) assertTrue(lines.contains(line));
        int archivedStart = lines.indexOf("--- archived alerts ---") + 1;
        assertTrue(archivedStart > 0);
        assertEquals(snapshot.archivedAlerts(), lines.subList(archivedStart, lines.size()));
    }

    private static void assertCoherent(FarmState.Snapshot snapshot) {
        int generated = snapshot.totalAlertsGenerated();
        assertEquals(generated, snapshot.totalAlerts() + snapshot.archivedAlerts().size());
        assertEquals(generated, snapshot.sensorLogs().size());
        assertEquals(generated, snapshot.chamberSummaries().values().stream()
            .mapToInt(FarmState.SummarySnapshot::alerts).sum());
        assertEquals(generated * 15, snapshot.totalEnergyUsage());
        List<String> combined = new ArrayList<>(snapshot.alerts());
        combined.addAll(snapshot.archivedAlerts());
        assertEquals(generated, new HashSet<>(combined).size());
    }
}
