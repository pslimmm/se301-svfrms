package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;

import java.util.*;

/**
 * Concrete formatter producing plain text reports identical to legacy system output.
 */
public class PlainTextReportFormatter implements ReportFormatterStrategy {

    @Override
    public List<String> formatReport(
            Date timestamp,
            long totalAlertsGenerated,
            long activeAlerts,
            long waterUsage,
            long energyUsage,
            List<String> reportEvents,
            Map<ChamberId, ChamberSummarySnapshot> chamberSummaries,
            List<AlertEvent> archivedAlerts) {

        List<String> lines = new ArrayList<>();
        lines.add("===== DAILY REPORT =====");
        lines.add("Timestamp: " + timestamp);
        lines.add("Generated Alerts: " + totalAlertsGenerated);
        lines.add("Active Alerts: " + activeAlerts);
        lines.add("Water Usage: " + waterUsage + " L");
        lines.add("Energy Usage: " + energyUsage + " kWh");
        lines.add("Archived alerts: " + (archivedAlerts != null ? archivedAlerts.size() : 0));
        lines.add("---");

        if (reportEvents != null) {
            lines.addAll(reportEvents);
        }

        if (chamberSummaries != null && !chamberSummaries.isEmpty()) {
            lines.add("--- chamber wise summary ---");
            List<ChamberId> sortedChambers = new ArrayList<>(chamberSummaries.keySet());
            sortedChambers.sort(Comparator.comparing(ChamberId::value));

            for (ChamberId chamberId : sortedChambers) {
                ChamberSummarySnapshot summary = chamberSummaries.get(chamberId);
                lines.add(chamberId.value() + " -> Alerts=" + summary.alerts() +
                        ", Actions=" + summary.mitigationActions() +
                        ", Last Action=" + summary.lastAction() +
                        ", Last Alert=" + summary.lastAlert());
            }
        }

        if (archivedAlerts != null && !archivedAlerts.isEmpty()) {
            lines.add("--- archived alerts ---");
            for (AlertEvent alert : archivedAlerts) {
                lines.add(alert.message());
            }
        }

        return lines;
    }
}
