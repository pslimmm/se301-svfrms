package org.example.svfrms.domain.chamber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Counts and corresponding last messages commit as one immutable state. */
public final class ChamberSummaryTracker {
    private final AtomicReference<ChamberSummarySnapshot> summary = new AtomicReference<>(
            new ChamberSummarySnapshot(0, 0, "No action", "None"));

    public void recordAlert(String message) {
        Objects.requireNonNull(message, "message");
        summary.updateAndGet(s -> new ChamberSummarySnapshot(s.alerts() + 1, s.mitigationActions(), s.lastAction(), message));
    }

    public void recordMitigationAction(String action) {
        Objects.requireNonNull(action, "action");
        summary.updateAndGet(s -> new ChamberSummarySnapshot(s.alerts(), s.mitigationActions() + 1, action, s.lastAlert()));
    }

    public ChamberSummarySnapshot snapshot() { return summary.get(); }
}
