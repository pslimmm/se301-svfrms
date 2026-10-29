package org.example.svfrms.domain.chamber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** Concurrent counters and latest status for one chamber. */
public final class ChamberSummaryTracker {
    private final LongAdder alerts = new LongAdder();
    private final LongAdder mitigationActions = new LongAdder();
    private final AtomicReference<String> lastAction = new AtomicReference<>("No action");
    private final AtomicReference<String> lastAlert = new AtomicReference<>("None");

    public void recordAlert(String message) {
        alerts.increment();
        lastAlert.set(Objects.requireNonNull(message, "message"));
    }

    public void recordMitigationAction(String action) {
        mitigationActions.increment();
        lastAction.set(Objects.requireNonNull(action, "action"));
    }

    public ChamberSummarySnapshot snapshot() {
        return new ChamberSummarySnapshot(Math.toIntExact(alerts.sum()), Math.toIntExact(mitigationActions.sum()),
                lastAction.get(), lastAlert.get());
    }
}
