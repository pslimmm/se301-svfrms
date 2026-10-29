package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable input to operational report formatters. */
public record DailyReportSnapshot(Instant generatedAt, long generatedAlerts, int activeAlerts,
                                  long waterUsage, long energyUsage, List<String> reportEntries,
                                  Map<ChamberId, ChamberSummarySnapshot> chamberSummaries,
                                  List<AlertEvent> archivedAlerts) {
    public DailyReportSnapshot {
        Objects.requireNonNull(generatedAt, "generatedAt");
        if (generatedAlerts < 0 || activeAlerts < 0 || waterUsage < 0 || energyUsage < 0) {
            throw new IllegalArgumentException("Report counters must be non-negative");
        }
        reportEntries = List.copyOf(reportEntries);
        chamberSummaries = Map.copyOf(chamberSummaries);
        archivedAlerts = List.copyOf(archivedAlerts);
    }
}
