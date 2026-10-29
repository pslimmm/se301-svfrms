package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.infrastructure.storage.ReportStoragePort;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Report service coordinating console summary reports and file archive persistence.
 */
public class DailyReportService extends AbstractReportGenerator {

    private final ResourceAccountingService metrics;
    private final AlertManagementService alertService;
    private final Map<ChamberId, ChamberSummaryTracker> summaryMap;
    private final List<String> reportEvents = new CopyOnWriteArrayList<>();

    public DailyReportService(
            ResourceAccountingService metrics,
            AlertManagementService alertService,
            Map<ChamberId, ChamberSummaryTracker> summaryMap,
            ReportFormatterStrategy formatter,
            ReportStoragePort storagePort) {
        super(formatter, storagePort);
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.alertService = Objects.requireNonNull(alertService, "alertService must not be null");
        this.summaryMap = Objects.requireNonNull(summaryMap, "summaryMap must not be null");
    }

    /**
     * Outputs daily summary metrics to console matching legacy output format.
     */
    public void generateDailyReport() {
        System.out.println("===== DAILY REPORT =====");
        System.out.println("Generated Alerts: " + metrics.getTotalAlertsGenerated());
        System.out.println("Active Alerts: " + alertService.getActiveAlertsCount());
        System.out.println("Water Usage: " + metrics.getWaterUsageLiters() + " L");
        System.out.println("Energy Usage: " + metrics.getEnergyUsageKWh() + " kWh");
        recordReportEvent("Daily Report");
    }

    public void recordReportEvent(String event) {
        if (event != null) {
            reportEvents.add(event);
        }
    }

    public List<String> getReportEvents() {
        return Collections.unmodifiableList(reportEvents);
    }

    public List<String> saveArchiveReport(Path destination) throws IOException {
        return generateAndSave(destination);
    }

    @Override
    protected ReportData collectReportData() {
        Map<ChamberId, ChamberSummarySnapshot> snapshots = new HashMap<>();
        for (Map.Entry<ChamberId, ChamberSummaryTracker> entry : summaryMap.entrySet()) {
            snapshots.put(entry.getKey(), entry.getValue().getSnapshot());
        }

        List<AlertEvent> archived = alertService.getAllArchivedAlerts();

        return new ReportData(
                new Date(),
                metrics.getTotalAlertsGenerated(),
                alertService.getActiveAlertsCount(),
                metrics.getWaterUsageLiters(),
                metrics.getEnergyUsageKWh(),
                new ArrayList<>(reportEvents),
                snapshots,
                archived
        );
    }
}
