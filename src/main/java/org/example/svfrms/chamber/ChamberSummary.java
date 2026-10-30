package org.example.svfrms.chamber;

/**
 * Running totals for one chamber, shown in the "chamber wise summary" of the archive report.
 *
 * <p><b>Concurrency fix (C4):</b> alerts are recorded by chamber threads and actions by the device
 * thread. The legacy code did {@code summary.alerts++} with no lock and lost counts. All methods
 * here are synchronized, and {@link #snapshot()} returns an immutable copy for reports.
 */
public final class ChamberSummary {

    private int alerts;
    private int mitigationActions;
    private String lastAction = "No action";
    private String lastAlert = "None";

    public synchronized void recordAlert(String message) {
        alerts++;
        lastAlert = message;
    }

    public synchronized void recordAction(String message) {
        mitigationActions++;
        lastAction = message;
    }

    public synchronized ChamberSummarySnapshot snapshot() {
        return new ChamberSummarySnapshot(alerts, mitigationActions, lastAction, lastAlert);
    }
}
