package org.example.svfrms.domain.chamber;

import java.util.Objects;

/** Immutable copy of a chamber's alert and mitigation summary. */
public record ChamberSummarySnapshot(int alerts, int mitigationActions, String lastAction, String lastAlert) {
    public ChamberSummarySnapshot {
        if (alerts < 0 || mitigationActions < 0) {
            throw new IllegalArgumentException("Summary counters must be non-negative");
        }
        Objects.requireNonNull(lastAction, "lastAction");
        Objects.requireNonNull(lastAlert, "lastAlert");
    }

    public ChamberSummarySnapshot(ChamberSummarySnapshot source) {
        this(source.alerts, source.mitigationActions, source.lastAction, source.lastAlert);
    }
}
