package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.infrastructure.storage.FileReportStorageAdapter;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.resource.ResourceAccountingService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void generatedReportMatchesLegacyLineFormatAndWritesThroughStoragePort() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        summary.recordAlert("CH1 Tomato High Temp");
        summary.recordMitigationAction("Cooling Fan Activated");
        ResourceAccountingService resources = new ResourceAccountingService();
        resources.recordAlert();
        resources.recordWater(50);
        resources.recordEnergy(10);
        AlertManagementService alerts = new AlertManagementService();
        alerts.publishAlert(new AlertEvent(chamber, "Tomato", AlertSeverity.WARNING, "CH1 Tomato High Temp", NOW));
        alerts.archiveAlerts();
        Path reportFile = tempDir.resolve("archive/daily_report.txt");
        DailyReportService reports = new DailyReportService(resources, alerts, Map.of(chamber, summary),
                new PlainTextReportFormatter(), new FileReportStorageAdapter(reportFile),
                Clock.fixed(NOW, ZoneOffset.UTC), ignored -> {});
        reports.recordReportEntry("Daily Report");
        reports.recordReportEntry("Archived 1 alerts");

        List<String> generated = reports.generate();

        assertThat(generated).containsExactly(
                "===== DAILY REPORT =====",
                "Timestamp: " + new Date(NOW.toEpochMilli()),
                "Generated Alerts: 1",
                "Active Alerts: 0",
                "Water Usage: 50 L",
                "Energy Usage: 10 kWh",
                "Archived alerts: 1",
                "---",
                "Daily Report",
                "Archived 1 alerts",
                "--- chamber wise summary ---",
                "CH1 -> Alerts=1, Actions=1, Last Action=Cooling Fan Activated, Last Alert=CH1 Tomato High Temp",
                "--- archived alerts ---",
                "CH1 Tomato High Temp");
        assertThat(Files.readAllLines(reportFile, StandardCharsets.UTF_8)).containsExactlyElementsOf(generated);
    }

    @Test
    void reportAlertCountsRemainCoherentWhileAlertsArePublishedAndArchived() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        AlertManagementService alerts = new AlertManagementService();
        DailyReportService reports = new DailyReportService(new ResourceAccountingService(), alerts,
                Map.of(chamber, new ChamberSummaryTracker()), new PlainTextReportFormatter(),
                new FileReportStorageAdapter(tempDir.resolve("concurrent.txt")),
                Clock.fixed(NOW, ZoneOffset.UTC), ignored -> {});
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var publisher = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 1_000; index++) {
                    alerts.publishAlert(new AlertEvent(chamber, "Tomato", AlertSeverity.WARNING,
                            "Alert " + index, NOW.plusMillis(index)));
                    if (index % 2 == 0) alerts.archiveAlerts();
                }
                return null;
            });
            var reporter = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 200; index++) {
                    List<String> lines = reports.saveArchiveReport();
                    long generated = valueAfter(lines, "Generated Alerts: ");
                    long active = valueAfter(lines, "Active Alerts: ");
                    long archived = valueAfter(lines, "Archived alerts: ");
                    assertThat(generated).isEqualTo(active + archived);
                }
                return null;
            });
            start.countDown();
            publisher.get();
            reporter.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static long valueAfter(List<String> lines, String prefix) {
        String value = lines.stream().filter(line -> line.startsWith(prefix)).findFirst().orElseThrow()
                .substring(prefix.length()).trim();
        return Long.parseLong(value);
    }
}
