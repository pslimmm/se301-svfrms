package org.example.svfrms.service.report;

import org.example.svfrms.domain.chamber.*;

import java.util.*;

/** Plain-text formatter preserving the legacy daily report labels and section order. */
public final class PlainTextReportFormatter implements ReportFormatterStrategy {
    @Override
    public void formatHeader(DailyReportSnapshot snapshot, List<String> lines) {
        lines.add("===== DAILY REPORT =====");
        lines.add("Timestamp: " + new Date(snapshot.generatedAt().toEpochMilli()));
        lines.add("Generated Alerts: " + snapshot.generatedAlerts());
        lines.add("Active Alerts: " + snapshot.activeAlerts());
        lines.add("Water Usage: " + snapshot.waterUsage() + " L");
        lines.add("Energy Usage: " + snapshot.energyUsage() + " kWh");
        lines.add("Archived alerts: " + snapshot.archivedAlerts().size());
        lines.add("---");
    }

    @Override
    public void formatBody(DailyReportSnapshot snapshot, List<String> lines) {
        lines.addAll(snapshot.reportEntries());
        if (!snapshot.chamberSummaries().isEmpty()) {
            lines.add("--- chamber wise summary ---");
            for (Map.Entry<ChamberId, ChamberSummarySnapshot> entry
                    : snapshot.chamberSummaries().entrySet().stream()
                    .sorted(Comparator.comparing(item -> item.getKey().value())).toList()) {
                var summary = entry.getValue();
                lines.add(entry.getKey().value() + " -> Alerts=" + summary.alerts()
                        + ", Actions=" + summary.mitigationActions()
                        + ", Last Action=" + summary.lastAction()
                        + ", Last Alert=" + summary.lastAlert());
            }
        }
        if (!snapshot.archivedAlerts().isEmpty()) {
            lines.add("--- archived alerts ---");
            snapshot.archivedAlerts().stream().map(alert -> alert.message()).forEach(lines::add);
        }
    }
}
