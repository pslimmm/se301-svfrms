package org.example.svfrms;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.infrastructure.storage.FileReportStorageAdapter;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.report.DailyReportService;
import org.example.svfrms.service.report.DailyReportSnapshot;
import org.example.svfrms.service.report.PlainTextReportFormatter;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.ConsoleLoggingObserver;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.example.svfrms.service.telemetry.ThresholdEvaluationObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenMasterLegacyTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void referenceSourceIsPinnedOriginal() throws Exception {
        byte[] bytes = LegacyReferenceHarness.resource("SVFRMS.java.txt").getBytes(StandardCharsets.UTF_8);
        // Git hashes the canonical LF blob, including its trailing LF; no source edits are permitted.
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
        digest.update(bytes);
        assertThat(HexFormat.of().formatHex(digest.digest()))
                .isEqualTo("ddcb50768c159274ce274e70732bba70f259e0ac");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("\r");
        // run() also asserts the child-recorded code source is its isolated classes directory.
        assertThat(LegacyReferenceHarness.run(tempDir.resolve("provenance")))
                .isEqualTo(LegacyReferenceHarness.fixture());
    }

    @Test
    void referenceScriptProducesStableFixtures() throws Exception {
        LegacyReferenceHarness.Result first = LegacyReferenceHarness.run(tempDir.resolve("first"));
        LegacyReferenceHarness.Result second = LegacyReferenceHarness.run(tempDir.resolve("second"));
        assertThat(first).isEqualTo(second).isEqualTo(LegacyReferenceHarness.fixture());
        assertThat(first.sensorLogs().lines().count()).isEqualTo(555);
        assertThat(first.observations()).contains("\tSTATE\tINITIAL\t24.0\t70.0\t60.0\t600.0\t70.0",
                "\tSUMMARY\tINITIAL\t0\t0\tNo action\tNone");
    }

    @Test
    void goldenMasterDetectsThresholdAndCostDrift() throws Exception {
        String original = LegacyReferenceHarness.resource("SVFRMS.java.txt");
        String thresholdMutation = original.replace("if(value>28)", "if(value>29)");
        String costMutation = original.replace("chamber+\" Tomato High Temp\"); totalEnergyUsage+=10;",
                "chamber+\" Tomato High Temp\"); totalEnergyUsage+=11;");
        assertThat(thresholdMutation).isNotEqualTo(original);
        assertThat(costMutation).isNotEqualTo(original);
        LegacyReferenceHarness.Result expected = LegacyReferenceHarness.fixture();
        LegacyReferenceHarness.Result threshold = LegacyReferenceHarness.run(tempDir.resolve("threshold-mutant"), thresholdMutation);
        LegacyReferenceHarness.Result cost = LegacyReferenceHarness.run(tempDir.resolve("cost-mutant"), costMutation);
        // These are actual compiled original mutations; each relevant full comparison must reject them.
        assertThatThrownBy(() -> assertThat(threshold.console()).isEqualTo(expected.console()))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertThat(cost.observations()).isEqualTo(expected.observations()))
                .isInstanceOf(AssertionError.class);
        assertThat(LegacyReferenceHarness.resource("SVFRMS.java.txt")).isEqualTo(original);
    }

    @Test
    void normalizationPreservesMessagesNumbersAnsiAndOrder() {
        String input = "Thu Oct 29 12:34:56 UTC 2026 : Processing Tomato Temperature = 29.0\r\n"
                + "Timestamp: Thu Oct 29 12:34:56 UTC 2026\r\n"
                + "Archive report saved to: C:\\temporary\\archive\\daily_report.txt\r\n"
                + "\t\u001B[32m[CH1][CONTROL] Temperature reduced to 24.0C\u001B[0m\r\n"
                + "[CH1][Tomato][ALERT] CH1 Tomato High Temp\r\nEnergy Usage: 10 kWh\r\n";
        assertThat(LegacyReferenceHarness.normalize(input)).isEqualTo(
                "<TIMESTAMP> : Processing Tomato Temperature = 29.0\nTimestamp: <TIMESTAMP>\n"
                + "Archive report saved to: <REPORT_PATH>\n"
                + "\t\u001B[32m[CH1][CONTROL] Temperature reduced to 24.0C\u001B[0m\n"
                + "[CH1][Tomato][ALERT] CH1 Tomato High Temp\nEnergy Usage: 10 kWh\n");
    }

    @Test
    void fixedClockKeepsExactModernTimestampRendering() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            List<String> logs = new ArrayList<>();
            new ConsoleLoggingObserver(logs::add, Clock.fixed(NOW, ZoneOffset.UTC))
                    .onTelemetry(new SensorReading(new ChamberId("CH1"), "Tomato", SensorType.TEMPERATURE, 29, NOW));
            assertThat(logs).containsExactly("Thu Oct 29 00:00:00 UTC 2026 : Processing Tomato Temperature = 29.0");
            List<String> header = new ArrayList<>();
            new PlainTextReportFormatter().formatHeader(new DailyReportSnapshot(NOW, 0, 0, 0, 0,
                    List.of(), Map.of(), List.of()), header);
            assertThat(header).containsExactly("===== DAILY REPORT =====",
                    "Timestamp: Thu Oct 29 00:00:00 UTC 2026", "Generated Alerts: 0", "Active Alerts: 0",
                    "Water Usage: 0 L", "Energy Usage: 0 kWh", "Archived alerts: 0", "---");
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void referenceChildTimeoutAndInterruptionAreBoundedAndReaped() throws Exception {
        Path sleeper = tempDir.resolve("BlockingChild.java");
        Files.writeString(sleeper, "class BlockingChild { public static void main(String[] args) throws Exception { Thread.sleep(60000); } }");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process timeoutChild = new ProcessBuilder(javaExecutable, sleeper.toString()).start();
        assertThatThrownBy(() -> LegacyReferenceHarness.awaitExit(timeoutChild, java.time.Duration.ofMillis(200)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("timed out");
        assertThat(timeoutChild.isAlive()).isFalse();

        Process interruptedChild = new ProcessBuilder(javaExecutable, sleeper.toString()).start();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> LegacyReferenceHarness.awaitExit(interruptedChild, java.time.Duration.ofSeconds(30)))
                    .isInstanceOf(InterruptedException.class);
            assertThat(interruptedChild.isAlive()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void preservesLegacyOutputAndArchiveFormatForDeterministicSensorInput() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24, 70, 60, 600, 70));
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        ResourceAccountingService metrics = new ResourceAccountingService();
        CropRegistry crops = CropRegistry.withStandardProfiles();
        AlertManagementService alerts = new AlertManagementService();
        List<String> output = new CopyOnWriteArrayList<>();
        List<String> sensorLogs = new CopyOnWriteArrayList<>();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        ActuatorExecutionService actuators = new ActuatorExecutionService(
                new BoundedControlQueue(), Map.of(chamber, state), Map.of(chamber, summary), metrics, output::add);
        actuators.start();
        TelemetryPublisher telemetry = new TelemetryPublisher(failure -> {
            throw failure;
        });
        telemetry.subscribe(new ConsoleLoggingObserver(sensorLogs::add, clock));
        telemetry.subscribe(new ThresholdEvaluationObserver(crops, new DefaultCropThresholdStrategy(crops, clock),
                alerts, actuators, metrics, Map.of(chamber, state), Map.of(chamber, summary),
                java.time.Duration.ofSeconds(1), output::add));
        Path reportFile = tempDir.resolve("archive/daily_report.txt");
        DailyReportService reports = new DailyReportService(metrics, alerts, Map.of(chamber, summary),
                new PlainTextReportFormatter(), new FileReportStorageAdapter(reportFile), clock, output::add);

        try {
            telemetry.publish(new SensorReading(chamber, "Tomato", SensorType.TEMPERATURE, 29, NOW));
        } finally {
            actuators.close();
        }
        reports.recordDailyReport();
        reports.archiveAlerts();
        List<String> report = reports.saveArchiveReport();

        assertThat(sensorLogs).singleElement().asString().endsWith(" : Processing Tomato Temperature = 29.0");
        assertThat(output).contains(
                "[CH1][Tomato][ALERT] CH1 Tomato High Temp",
                "\t\u001B[32m[CH1][CONTROL] Cooling Fan Activated\u001B[0m",
                "\t\u001B[32m[CH1][CONTROL] Temperature reduced to 24.0C\u001B[0m",
                "===== DAILY REPORT =====",
                "Generated Alerts: 1",
                "Active Alerts: 1",
                "Water Usage: 0 L",
                "Energy Usage: 10 kWh",
                "Archiving alerts... 1 moved to archive.");
        assertThat(report).containsExactly(
                "===== DAILY REPORT =====",
                "Timestamp: " + new Date(NOW.toEpochMilli()),
                "Generated Alerts: 1",
                "Active Alerts: 0",
                "Water Usage: 0 L",
                "Energy Usage: 10 kWh",
                "Archived alerts: 1",
                "---",
                "Daily Report",
                "Archived 1 alerts",
                "--- chamber wise summary ---",
                "CH1 -> Alerts=1, Actions=1, Last Action=Cooling Fan Activated, Last Alert=CH1 Tomato High Temp",
                "--- archived alerts ---",
                "CH1 Tomato High Temp");
        assertThat(Files.readAllLines(reportFile, StandardCharsets.UTF_8)).containsExactlyElementsOf(report);
    }
}
