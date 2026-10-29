package org.example.svfrms.domain.chamber;

/**
 * Immutable point-in-time operational summary for a chamber.
 */
public record ChamberSummarySnapshot(
        int alerts,
        int mitigationActions,
        String lastAction,
        String lastAlert
) {
    public static ChamberSummarySnapshot empty() {
        return new ChamberSummarySnapshot(0, 0, "No action", "None");
    }
}
