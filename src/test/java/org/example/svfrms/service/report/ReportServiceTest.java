package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.infrastructure.storage.FileReportStorageAdapter;
import org.example.svfrms.infrastructure.storage.ReportStoragePort;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 8: Report Generation Pipeline Tests (Template Method & Formatter)")
public class ReportServiceTest {

    private ResourceAccountingService metrics;
    private AlertManagementService alertService;
    private Map<ChamberId, ChamberSummaryTracker> summaryMap;
    private ReportStoragePort storagePort;
    private DailyReportService reportService;

    @BeforeEach
    void setUp() {
        metrics = new ResourceAccountingService();
        alertService = new AlertManagementService();
        summaryMap = new ConcurrentHashMap<>();
        storagePort = new FileReportStorageAdapter();

        ChamberSummaryTracker ch1 = new ChamberSummaryTracker(ChamberId.of("CH1"));
        ch1.recordAlert("CH1 Tomato High Temp");
        ch1.recordAction("Cooling Fan Activated");
        summaryMap.put(ChamberId.of("CH1"), ch1);

        reportService = new DailyReportService(
                metrics,
                alertService,
                summaryMap,
                new PlainTextReportFormatter(),
                storagePort
        );
    }

    @Test
    @DisplayName("generateDailyReport prints exact legacy console output and records report event")
    void testDailyReportConsoleOutput() {
        AlertEvent alert = new AlertEvent(ChamberId.of("CH1"), "Tomato", AlertSeverity.WARNING, "CH1 Tomato High Temp", Instant.now());
        alertService.publishAlert(alert);
        metrics.recordAlertGenerated();
        metrics.recordEnergyUsage(100);
        metrics.recordWaterUsage(250);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            reportService.generateDailyReport();
        } finally {
            System.setOut(origOut);
        }

        String console = out.toString();
        assertThat(console).contains("===== DAILY REPORT =====");
        assertThat(console).contains("Generated Alerts: 1");
        assertThat(console).contains("Active Alerts: 1");
        assertThat(console).contains("Water Usage: 250 L");
        assertThat(console).contains("Energy Usage: 100 kWh");
        assertThat(reportService.getReportEvents()).contains("Daily Report");
    }

    @Test
    @DisplayName("generateArchiveReport produces exact report lines and saves to destination file")
    void testArchiveReportGenerationAndStorage(@TempDir Path tempDir) throws Exception {
        metrics.recordAlertGenerated();
        metrics.recordEnergyUsage(50);
        metrics.recordWaterUsage(80);

        AlertEvent alert = new AlertEvent(ChamberId.of("CH1"), "Tomato", AlertSeverity.WARNING, "CH1 Tomato High Temp", Instant.now());
        alertService.publishAlert(alert);
        alertService.archiveAlerts();
        reportService.recordReportEvent("Archived 1 alerts");

        Path reportFile = tempDir.resolve("archive").resolve("daily_report.txt");
        List<String> lines = reportService.saveArchiveReport(reportFile);

        assertThat(lines).contains("===== DAILY REPORT =====");
        assertThat(lines).anyMatch(l -> l.startsWith("Timestamp: "));
        assertThat(lines).contains("Generated Alerts: 1");
        assertThat(lines).contains("Water Usage: 80 L");
        assertThat(lines).contains("Energy Usage: 50 kWh");
        assertThat(lines).contains("Archived alerts: 1");
        assertThat(lines).contains("---");
        assertThat(lines).contains("Archived 1 alerts");
        assertThat(lines).contains("--- chamber wise summary ---");
        assertThat(lines).contains("CH1 -> Alerts=1, Actions=1, Last Action=Cooling Fan Activated, Last Alert=CH1 Tomato High Temp");
        assertThat(lines).contains("--- archived alerts ---");
        assertThat(lines).contains("CH1 Tomato High Temp");

        assertThat(Files.exists(reportFile)).isTrue();
        List<String> savedLines = Files.readAllLines(reportFile);
        assertThat(savedLines).isEqualTo(lines);
    }
}
