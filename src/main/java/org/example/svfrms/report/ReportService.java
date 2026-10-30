package org.example.svfrms.report;

import java.io.IOException;
import java.util.List;

/**
 * Produces the reports: take one snapshot, give it to a generator, and send the lines to a writer.
 * It depends only on the {@link ReportGenerator} and {@link ReportWriter} interfaces, so new reports or
 * new destinations plug in without changes here.
 */
public final class ReportService {

    private final SnapshotCollector collector;
    private final ReportLog reportLog;
    private final ReportSet reports;
    private final ReportWriter console;
    private final ReportWriter archiveFile;

    public ReportService(SnapshotCollector collector, ReportLog reportLog, ReportSet reports,
                         ReportWriter console, ReportWriter archiveFile) {
        this.collector = collector;
        this.reportLog = reportLog;
        this.reports = reports;
        this.console = console;
        this.archiveFile = archiveFile;
    }

    public void printDailyReport() throws IOException {
        console.write(reports.daily().generate(collector.capture()));
        reportLog.add("Daily Report");
    }

    public void saveArchiveReport() throws IOException {
        try {
            archiveFile.write(reports.archive().generate(collector.capture()));
            console.write(List.of("Archive report saved to: " + archiveFile.location()));
        } catch (IOException | RuntimeException e) {
            console.write(List.of("Could not save archive report: " + e.getMessage()));
        }
    }

    public void printExtraReports() throws IOException {
        for (ReportGenerator report : reports.extras()) {
            console.write(report.generate(collector.capture()));
        }
    }
}
