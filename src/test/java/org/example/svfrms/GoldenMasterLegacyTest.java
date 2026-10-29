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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenMasterLegacyTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @TempDir
    Path tempDir;

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
