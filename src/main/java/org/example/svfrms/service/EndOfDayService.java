package org.example.svfrms.service;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

import org.example.svfrms.alert.AlertArchiver;
import org.example.svfrms.report.ReportService;

/**
 * What happens after all chambers finish: archive the alerts, print the daily report, then save the
 * archive file.
 *
 * <p><b>Concurrency fix (C9):</b> the legacy code ran archive and report on two racing threads, so the
 * "Active Alerts" number changed from run to run. They now run one after the other, in the order the
 * legacy code started them (archive first).
 */
public final class EndOfDayService {

    private final AlertArchiver archiver;
    private final ReportService reports;
    private final PrintStream out;
    private final boolean includeExtraReports;

    public EndOfDayService(AlertArchiver archiver, ReportService reports, PrintStream out, boolean includeExtraReports) {
        this.archiver = archiver;
        this.reports = reports;
        this.out = out;
        this.includeExtraReports = includeExtraReports;
    }

    public void run() {
        try {
            archiver.archive();
            reports.printDailyReport();
            out.println("System finished sensor processing.");
            reports.saveArchiveReport();
            if (includeExtraReports) {
                reports.printExtraReports();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
