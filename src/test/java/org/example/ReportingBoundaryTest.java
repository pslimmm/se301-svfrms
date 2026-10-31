package org.example;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class ReportingBoundaryTest {
    private static final Instant INSTANT = Instant.parse("2026-10-30T12:34:56Z");
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"UTC, Fri Oct 30 12:34:56 UTC 2026", "Asia/Singapore, Fri Oct 30 20:34:56 GMT+08:00 2026"})
    void injectedClockControlsBothSensorLogsAndReportTimestamp(String zone, String expected) {
        Clock clock = Clock.fixed(INSTANT, ZoneId.of(zone));
        FarmState state = new FarmState(CropCatalog.legacyDefaults(), clock);
        state.processSensor("CH1", "Tomato", "Temperature", 25);
        state.handleEvent("LOG", "", "manual log");
        assertEquals(List.of(expected + " : Processing Tomato Temperature = 25.0", expected + " : manual log"),
            state.snapshot().sensorLogs());
        AtomicReference<List<String>> captured = new AtomicReference<>();
        ReportService reports = new ReportService(state, new ConsoleOutput(message -> {}),
            directory.resolve("report.txt"), (path, lines) -> captured.set(lines), clock);
        reports.saveArchiveReport();
        assertEquals(List.of("===== DAILY REPORT =====", "Timestamp: " + expected,
            "Generated Alerts: 0", "Active Alerts: 0", "Water Usage: 0 L", "Energy Usage: 0 kWh",
            "Archived alerts: 0", "---"), captured.get());
    }

    @Test
    void defaultZoneFormattingMatchesTheLegacyDateRepresentation() {
        Clock clock = Clock.fixed(INSTANT, ZoneId.systemDefault());
        assertEquals(Date.from(INSTANT).toString(), new LegacyTimestamp(clock).now());
    }

    @Test
    void completeScriptedRunProducesExactLogsAndBothPermittedReportHistoryOrders() throws Exception {
        Clock clock = Clock.fixed(INSTANT, ZoneId.of("Asia/Singapore"));
        SensorSource source = (chamber, sensor) -> switch (sensor) {
            case TEMPERATURE -> 24;
            case HUMIDITY -> 70;
            case SOIL -> 50;
            case LIGHT -> 600;
            case NUTRIENT -> 70;
        };
        Path report = directory.resolve("archive/daily_report.txt");
        ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
        FarmApplication app = new FarmApplication(CropCatalog.legacyDefaults(), source, clock,
            output::add, report, new FileArchiveWriter());
        app.run();
        var snapshot = app.snapshot();
        assertEquals(FarmApplication.RunState.COMPLETED, app.runState());
        assertTrue(app.workersTerminated());
        assertEquals(400, snapshot.sensorLogs().size());
        for (String reading : List.of("Temperature = 24.0", "Humidity = 70.0", "Soil = 50.0",
                "Light = 600.0", "Nutrient = 70.0")) {
            for (String crop : List.of("Tomato", "Strawberry", "Lettuce", "Chili")) {
                String line = "Fri Oct 30 20:34:56 GMT+08:00 2026 : Processing " + crop + " " + reading;
                assertEquals(20, snapshot.sensorLogs().stream().filter(line::equals).count());
            }
        }
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        assertEquals(33, lines.size());
        assertEquals(List.of("===== DAILY REPORT =====", "Timestamp: Fri Oct 30 20:34:56 GMT+08:00 2026",
            "Generated Alerts: 20", "Active Alerts: 0", "Water Usage: 160 L", "Energy Usage: 80 kWh",
            "Archived alerts: 20", "---"), lines.subList(0, 8));
        assertTrue(List.of(List.of("Daily Report", "Archived 20 alerts"),
            List.of("Archived 20 alerts", "Daily Report")).contains(lines.subList(8, 10)));
        assertEquals(snapshot.reports(), lines.subList(8, 10));
        assertEquals(List.of("--- chamber wise summary ---",
            "CH3 -> Alerts=20, Actions=20, Last Action=Cooling Fan Activated, Last Alert=CH3 Lettuce High Temp",
            "--- archived alerts ---"), lines.subList(10, 13));
        assertEquals(Set.of("CH3 Lettuce High Temp"), Set.copyOf(lines.subList(13, 33)));
        assertTrue(app.pendingControls().isEmpty());
        assertTrue(output.contains("Archive report saved to: " + report.toAbsolutePath()));
    }

    @Test
    void writerFailureRetainsTheLegacyErrorMessageAndDoesNotClaimSuccess() {
        List<String> output = new ArrayList<>();
        ArchiveWriter failing = (path, lines) -> { throw new IOException("disk full"); };
        ReportService reports = new ReportService(new FarmState(), new ConsoleOutput(output::add),
            directory.resolve("report.txt"), failing, Clock.fixed(INSTANT, ZoneId.of("UTC")));
        reports.saveArchiveReport();
        assertEquals(List.of("Could not save archive report: disk full"), output);
        assertFalse(Files.exists(directory.resolve("report.txt")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"security", "unchecked_io"})
    void uncheckedWriterFailureRetainsTheStandaloneLegacyErrorPolicy(String kind) {
        RuntimeException failure = kind.equals("security") ? new SecurityException("denied")
            : new UncheckedIOException("wrapped failure", new IOException("disk full"));
        List<String> output = new ArrayList<>();
        Path path = directory.resolve("report.txt");
        ReportService reports = new ReportService(new FarmState(), new ConsoleOutput(output::add),
            path, (target, lines) -> { throw failure; }, Clock.fixed(INSTANT, ZoneId.of("UTC")));

        assertDoesNotThrow(reports::saveArchiveReport);
        assertEquals(List.of("Could not save archive report: " + failure.getMessage()), output);
        assertFalse(Files.exists(path));
    }

    @Test
    void blockedWriterUsesADetachedSnapshotAndDoesNotBlockFarmUpdates() throws Exception {
        FarmState state = new FarmState();
        state.processSensor("CH1", "Tomato", "Temperature", 20);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<List<String>> saved = new AtomicReference<>();
        ArchiveWriter writer = (path, lines) -> {
            saved.set(lines);
            entered.countDown();
            await(release);
        };
        ReportService reports = new ReportService(state, new ConsoleOutput(message -> {}),
            directory.resolve("report.txt"), writer, Clock.fixed(INSTANT, ZoneId.of("UTC")));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writing = pool.submit(reports::saveArchiveReport);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            pool.submit(() -> {
                state.processSensor("CH1", "Tomato", "Temperature", 20);
                state.addUsage(100, 0);
            }).get(5, TimeUnit.SECONDS);
            assertEquals(2, state.snapshot().totalAlertsGenerated());
            assertTrue(saved.get().contains("Generated Alerts: 1"));
            assertTrue(saved.get().contains("Energy Usage: 15 kWh"));
            assertTrue(saved.get().contains("Water Usage: 0 L"));
            assertThrows(UnsupportedOperationException.class, () -> saved.get().clear());
            release.countDown();
            writing.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            shutdown(pool);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void injectedClockIsReadOutsideTheStateLock(boolean eventHelper) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(INSTANT, zone); }
            @Override public Instant instant() {
                entered.countDown();
                await(release);
                return INSTANT;
            }
        };
        FarmState state = new FarmState(CropCatalog.legacyDefaults(), clock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> logging = pool.submit(() -> {
                if (eventHelper) state.handleEvent("LOG", "", "message");
                else state.processSensor("CH1", "Tomato", "Temperature", 25);
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var snapshot = pool.submit(() -> { state.addUsage(100, 20); return state.snapshot(); })
                .get(5, TimeUnit.SECONDS);
            assertEquals(100, snapshot.totalWaterUsage());
            assertTrue(snapshot.sensorLogs().isEmpty());
            release.countDown();
            logging.get(5, TimeUnit.SECONDS);
            assertEquals(1, state.snapshot().sensorLogs().size());
            assertTrue(state.snapshot().sensorLogs().getFirst().startsWith("Fri Oct 30 12:34:56 UTC 2026 : "));
        } finally {
            release.countDown();
            shutdown(pool);
        }
    }

    @Test
    void filesystemWriterCreatesDirectoriesWritesUtf8AndTruncates() throws Exception {
        Path path = directory.resolve("nested/archive/report.txt");
        ArchiveWriter writer = new FileArchiveWriter();
        writer.write(path, List.of("Bok choy \u83dc", "old trailing line"));
        assertEquals(List.of("Bok choy \u83dc", "old trailing line"), Files.readAllLines(path, StandardCharsets.UTF_8));
        writer.write(path, List.of("short"));
        assertEquals(List.of("short"), Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(8, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("fixture interrupted");
        }
    }

    private static void shutdown(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}
