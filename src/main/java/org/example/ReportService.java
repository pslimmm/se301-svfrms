package org.example;

import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Captures coherent snapshots, then formats and writes them outside the state lock. */
public final class ReportService {
    private final FarmState state;
    private final ConsoleOutput output;
    private final Path reportFile;
    private final ArchiveWriter writer;
    private final LegacyTimestamp timestamp;

    public ReportService(FarmState state, ConsoleOutput output, Path reportFile, ArchiveWriter writer, Clock clock) {
        this.state = Objects.requireNonNull(state);
        this.output = Objects.requireNonNull(output);
        this.reportFile = Objects.requireNonNull(reportFile);
        this.writer = Objects.requireNonNull(writer);
        timestamp = new LegacyTimestamp(clock);
    }

    public void generateDailyReport() {
        output.lines(dailyLines(state.dailyReportSnapshot()));
    }

    public void archiveAlerts() {
        int count = state.archiveAlerts();
        output.line(count == 0 ? "Archiving alerts... no active alerts to archive."
            : "Archiving alerts... " + count + " moved to archive.");
    }

    public void saveArchiveReport() {
        try {
            saveArchiveReportStrict();
        } catch (UncheckedIOException ignored) {
            // Preserve the standalone legacy helper's error-reporting behavior.
        }
    }

    void saveArchiveReportStrict() {
        FarmState.Snapshot snapshot = state.snapshot();
        try {
            writer.write(reportFile, archiveLines(snapshot, timestamp.now()));
            output.line("Archive report saved to: " + reportFile.toAbsolutePath());
        } catch (IOException e) {
            output.line("Could not save archive report: " + e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> dailyLines(FarmState.Snapshot snapshot) {
        return List.of("===== DAILY REPORT =====",
            "Generated Alerts: " + snapshot.totalAlertsGenerated(),
            "Active Alerts: " + snapshot.totalAlerts(),
            "Water Usage: " + snapshot.totalWaterUsage() + " L",
            "Energy Usage: " + snapshot.totalEnergyUsage() + " kWh");
    }

    private static List<String> archiveLines(FarmState.Snapshot snapshot, String time) {
        List<String> lines = new ArrayList<>(dailyLines(snapshot));
        lines.add(1, "Timestamp: " + time);
        lines.add("Archived alerts: " + snapshot.archivedAlerts().size());
        lines.add("---");
        lines.addAll(snapshot.reports());
        if (!snapshot.chamberSummaries().isEmpty()) {
            lines.add("--- chamber wise summary ---");
            for (String chamber : new TreeSet<>(snapshot.chamberSummaries().keySet())) {
                var summary = snapshot.chamberSummaries().get(chamber);
                lines.add(chamber + " -> Alerts=" + summary.alerts() + ", Actions=" + summary.mitigationActions()
                    + ", Last Action=" + summary.lastAction() + ", Last Alert=" + summary.lastAlert());
            }
        }
        if (!snapshot.archivedAlerts().isEmpty()) {
            lines.add("--- archived alerts ---");
            lines.addAll(snapshot.archivedAlerts());
        }
        return List.copyOf(lines);
    }
}
