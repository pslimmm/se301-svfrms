package org.example.svfrms.chamber;

/** Immutable copy of a {@link ChamberSummary} taken at one moment, safe to pass to reports. */
public record ChamberSummarySnapshot(int alerts, int mitigationActions, String lastAction, String lastAlert) {
}
