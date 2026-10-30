package org.example.svfrms.report;

import java.util.List;
import java.util.Map;

import org.example.svfrms.chamber.ChamberSummarySnapshot;
import org.example.svfrms.model.Alert;

/** The detailed report saved to {@code archive/daily_report.txt} (legacy {@code saveArchiveReport()}). */
public final class ArchiveReport extends TemplateReport {

    @Override
    protected String title() {
        return "DAILY REPORT";
    }

    @Override
    protected void addBody(FacilitySnapshot snapshot, List<String> lines) {
        lines.add("Timestamp: " + snapshot.timestamp());
        addTotals(snapshot, lines);
        lines.add("Archived alerts: " + snapshot.archivedAlerts().size());
        lines.add("---");
        lines.addAll(snapshot.reportLog());

        if (!snapshot.chambers().isEmpty()) {
            lines.add("--- chamber wise summary ---");
            for (Map.Entry<String, ChamberSummarySnapshot> entry : snapshot.chambers().entrySet()) {
                ChamberSummarySnapshot s = entry.getValue();
                lines.add(entry.getKey() + " -> Alerts=" + s.alerts() + ", Actions=" + s.mitigationActions()
                        + ", Last Action=" + s.lastAction() + ", Last Alert=" + s.lastAlert());
            }
        }

        if (!snapshot.archivedAlerts().isEmpty()) {
            lines.add("--- archived alerts ---");
            for (Alert alert : snapshot.archivedAlerts()) {
                lines.add(alert.message());
            }
        }
    }
}
