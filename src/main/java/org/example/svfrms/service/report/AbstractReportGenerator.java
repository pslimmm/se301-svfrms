package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import org.example.svfrms.infrastructure.storage.ReportStoragePort;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Skeleton report generator implementing the GoF Template Method pattern.
 */
public abstract class AbstractReportGenerator {

    protected final ReportFormatterStrategy formatter;
    protected final ReportStoragePort storagePort;

    public record ReportData(
            Date timestamp,
            long totalAlertsGenerated,
            long activeAlerts,
            long waterUsage,
            long energyUsage,
            List<String> reportEvents,
            Map<ChamberId, ChamberSummarySnapshot> chamberSummaries,
            List<AlertEvent> archivedAlerts
    ) {}

    public AbstractReportGenerator(ReportFormatterStrategy formatter, ReportStoragePort storagePort) {
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
    }

    /**
     * Template method executing the standardized report generation and persistence algorithm.
     */
    public final List<String> generateAndSave(Path destination) throws IOException {
        ReportData data = collectReportData();
        List<String> lines = formatter.formatReport(
                data.timestamp(),
                data.totalAlertsGenerated(),
                data.activeAlerts(),
                data.waterUsage(),
                data.energyUsage(),
                data.reportEvents(),
                data.chamberSummaries(),
                data.archivedAlerts()
        );

        if (destination != null) {
            storagePort.saveReport(destination, lines);
            System.out.println("Archive report saved to: " + destination.toAbsolutePath());
        }
        return lines;
    }

    /**
     * Primitive operation for gathering state from system services.
     */
    protected abstract ReportData collectReportData();
}
