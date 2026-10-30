package org.example.svfrms.alert;

import java.io.PrintStream;

import org.example.svfrms.report.ReportLog;

/**
 * The legacy {@code archiveAlerts()} step: move active alerts to the archive, note it in the report log
 * and print the same message as before.
 *
 * <p>The legacy version held {@code alertLock} and then took {@code reportLock} inside it (nested locks,
 * issue C7). Here the two steps are separate: {@link AlertService#archiveActive()} finishes and releases
 * its lock before {@link ReportLog#add} takes its own, so no thread ever holds both.
 */
public final class AlertArchiver {

    private final AlertService alerts;
    private final ReportLog reportLog;
    private final PrintStream out;

    public AlertArchiver(AlertService alerts, ReportLog reportLog, PrintStream out) {
        this.alerts = alerts;
        this.reportLog = reportLog;
        this.out = out;
    }

    public void archive() {
        int moved = alerts.archiveActive();
        if (moved > 0) {
            reportLog.add("Archived " + moved + " alerts");
            out.println("Archiving alerts... " + moved + " moved to archive.");
        } else {
            reportLog.add("No alerts to archive");
            out.println("Archiving alerts... no active alerts to archive.");
        }
    }
}
