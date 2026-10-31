package org.example.svfrms;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.example.SVFRMS;
import org.example.svfrms.concurrency.ChamberSimulationTask;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyOperationsTest {
    @TempDir Path temp;
    @Test
    void mainLeavesItsCompletedSessionObservableAndNextRunStartsFresh() throws Exception {
        var original = System.out;
        var bytes = new java.io.ByteArrayOutputStream();
        try (var captured = new java.io.PrintStream(bytes)) {
            System.setOut(captured);
            for (int run=0;run<2;run++) {
                SVFRMS.main(new String[0]);
                bytes.reset();
                SVFRMS.showLogs();
                assertThat(bytes.toString().lines().filter(line -> line.contains(" : Processing ")).count()).isEqualTo(400);
                assertThat(SVFRMS.getChamberSummary("CH1").mitigationActions()).isGreaterThan(0);
            }
        } finally { SVFRMS.closeSession(); System.setOut(original); }
    }

    @Test
    void blankChamberIdentifiersKeepTheirLegacyStateAndOutputEffects() throws Exception {
        var lines = new ArrayList<String>();
        try (var context = new SvfrmsApplicationContext(temp.resolve("blank.txt"), Clock.systemUTC(),lines::add,42)) {
            for (String chamber : List.of("", "  ")) {
                context.processSensor(chamber,"Unknown","Light",400);
                context.awaitControls(Duration.ofSeconds(2));
                assertThat(context.getChamberState(chamber).lightLevel()).isEqualTo(550);
                assertThat(context.getChamberSummary(chamber).alerts()).isEqualTo(1);
                assertThat(lines).contains("["+chamber+"][Unknown][ALERT] "+chamber+" Low Light Level");
            }
        }
    }
    @Test
    void compatibilitySessionStartsWithoutEagerChambers() {
        try (var context = new SvfrmsApplicationContext(Path.of("target/task3-report.txt"), Clock.systemUTC(), ignored -> {}, 42)) {
            assertThat(context.chamberStates()).isEmpty();
            assertThat(context.chamberSummaries()).isEmpty();
        }
    }

    @Test
    void legacyFacadeOperationsMatchReference() throws Exception {
        var original = System.out;
        var bytes = new java.io.ByteArrayOutputStream();
        try (var captured = new java.io.PrintStream(bytes)) {
            System.setOut(captured);
            SVFRMS.closeSession();
            assertThat(SVFRMS.getChamberState("NEW")).isEqualTo(new ChamberSnapshot(24,70,60,600,70));
            SVFRMS.handleEvent("LOG", "NEW", "Tomato", "manual log");
            SVFRMS.handleEvent("ALERT_LOG", "NEW", "Tomato", "printed alert");
            SVFRMS.handleEvent("CONTROL_LOG", "NEW", "", "printed control");
            SVFRMS.handleEvent("CREATE_ALERT", "NEW", "Tomato", "created");
            assertThat(SVFRMS.getChamberSummary("NEW").alerts()).isZero();
            SVFRMS.handleEvent("TRACK_ALERT", "NEW", "", "tracked");
            SVFRMS.handleEvent("TRACK_ACTION", "NEW", "", "manual action");
            SVFRMS.handleEvent("unknown", "NEW", "", "ignored");
            SVFRMS.activateControl("NEW", "Unknown");
            SVFRMS.processSensor("NEW", "Tomato", "temperature", 999);
            SVFRMS.addControlRequest("NEW", "Water Pump");
            SVFRMS.awaitControls(Duration.ofSeconds(2));
            SVFRMS.irrigationCycle();
            SVFRMS.feedingCycle();
            SVFRMS.showAlerts();
            SVFRMS.showLogs();
            SVFRMS.generateDailyReport();
            SVFRMS.archiveAlerts();
            SVFRMS.saveArchiveReport();
            assertThat(SVFRMS.getChamberState("NEW").soilMoisture()).isEqualTo(80);
            assertThat(SVFRMS.getChamberState("NEW").temperature()).isEqualTo(24);
            assertThat(SVFRMS.getChamberSummary("NEW")).isEqualTo(new ChamberSummarySnapshot(1,2,"Water Pump Activated","tracked"));
            String output = bytes.toString();
            assertThat(output).contains("[NEW][Tomato][ALERT] created", "\t\u001B[32m[NEW][CONTROL] printed control\u001B[0m", "manual log", "Processing Tomato temperature = 999.0", "Generated Alerts: 1", "Water Usage: 100 L", "Energy Usage: 20 kWh");
            SVFRMS.closeSession();
            assertThat(SVFRMS.getChamberState("NEW")).isEqualTo(new ChamberSnapshot(24,70,60,600,70));
            assertThat(SVFRMS.getChamberSummary("NEW")).isEqualTo(new ChamberSummarySnapshot(0,0,"No action","None"));
        } finally {
            SVFRMS.closeSession();
            System.setOut(original);
        }
    }

    @Test
    void modernFullScriptMatchesEveryFrozenReferenceField() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            var lines = new ArrayList<String>();
            var observations = new ArrayList<String>();
            Path report = temp.resolve("archive/daily_report.txt");
            Clock clock = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
            try (var context = new SvfrmsApplicationContext(report, clock, lines::add, 42)) {
                int index = 0;
                for (var step : LegacyReferenceHarness.script()) {
                    switch (step.op()) {
                        case "INIT" -> { context.getChamberState(step.chamber()); context.getChamberSummary(step.chamber()); }
                        case "READ" -> { context.processSensor(step.chamber(), step.crop(), step.sensor(), Double.parseDouble(step.value())); context.awaitControls(Duration.ofSeconds(2)); }
                        case "CONTROL" -> context.activateControl(step.chamber(), step.sensor());
                        case "IRRIGATE" -> context.irrigationCycle();
                        case "FEED" -> context.feedingCycle();
                        case "SIMULATE" -> {
                            int[] readings = Arrays.stream(step.value().split(",")).mapToInt(Integer::parseInt).toArray();
                            int[] offsets = {10,40,20,300,20};
                            var random = new Random(0) { int next; @Override public int nextInt(int bound) { int sensor = next++ % 5; return readings[sensor] - offsets[sensor]; } };
                            new ChamberSimulationTask(new ChamberId(step.chamber()), step.crop(), 20, context.telemetry(), context.resources(), random, clock).run();
                            context.awaitControls(Duration.ofSeconds(2));
                        }
                        case "REPORT" -> context.generateDailyReport();
                        case "ARCHIVE" -> context.archiveAlerts();
                        case "SHOW_ALERTS" -> context.showAlerts();
                        case "SHOW_LOGS" -> context.showLogs();
                        case "SAVE" -> context.saveArchiveReport();
                        default -> throw new AssertionError(step);
                    }
                    String prefix = String.format(Locale.ROOT,"%03d", index++) + "\t";
                    observations.add(prefix + "STEP\t" + String.join("\t", step.op(),step.chamber(),step.crop(),step.sensor(),step.value()));
                    var alerts = context.alerts().snapshot();
                    observations.add(prefix + "COUNTERS\t" + context.resources().waterUsage() + "\t" + context.resources().energyUsage() + "\t" + alerts.activeAlerts().size() + "\t" + alerts.generatedAlertCount() + "\t" + alerts.archivedAlerts().size() + "\t" + context.sensorLogs().size() + "\t0");
                    context.chamberStates().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().value())).forEach(e -> {
                        var s = e.getValue(); observations.add(prefix + "STATE\t" + e.getKey().value() + "\t" + s.temperature() + "\t" + s.humidity() + "\t" + s.soilMoisture() + "\t" + s.lightLevel() + "\t" + s.nutrientLevel());
                    });
                    context.chamberSummaries().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().value())).forEach(e -> {
                        var s = e.getValue(); observations.add(prefix + "SUMMARY\t" + e.getKey().value() + "\t" + s.alerts() + "\t" + s.mitigationActions() + "\t" + s.lastAction() + "\t" + s.lastAlert());
                    });
                    alerts.activeAlerts().forEach(a -> observations.add(prefix + "ACTIVE\t" + a.message()));
                    alerts.archivedAlerts().forEach(a -> observations.add(prefix + "ARCHIVED\t" + a.message()));
                    context.reportEntries().forEach(r -> observations.add(prefix + "REPORT_ENTRY\t" + r));
                }
                var actual = new LegacyReferenceHarness.Result(normalize(lines),normalize(context.sensorLogs()),LegacyReferenceHarness.normalize(Files.readString(report)),normalize(observations));
                var expected = LegacyReferenceHarness.fixture();
                assertThat(actual.console()).as("complete console").isEqualTo(expected.console());
                assertThat(actual.sensorLogs()).as("retained sensor logs").isEqualTo(expected.sensorLogs());
                assertThat(actual.report()).as("saved report").isEqualTo(expected.report());
                assertThat(actual.observations()).as("every operation's state, summary, counters and stores").isEqualTo(expected.observations());
            }
        } finally { TimeZone.setDefault(previous); }
    }

    private static String normalize(List<String> lines) { return LegacyReferenceHarness.normalize(String.join("\n", lines) + "\n"); }
}
