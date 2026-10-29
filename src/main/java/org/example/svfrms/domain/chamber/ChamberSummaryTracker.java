package org.example.svfrms.domain.chamber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe accumulator for chamber-level operational summary statistics.
 */
public class ChamberSummaryTracker {

    private final ChamberId chamberId;
    private final AtomicInteger alertsCount = new AtomicInteger(0);
    private final AtomicInteger mitigationActionsCount = new AtomicInteger(0);
    private final AtomicReference<String> lastAction = new AtomicReference<>("No action");
    private final AtomicReference<String> lastAlert = new AtomicReference<>("None");

    public ChamberSummaryTracker(ChamberId chamberId) {
        this.chamberId = Objects.requireNonNull(chamberId, "chamberId must not be null");
    }

    public ChamberId getChamberId() {
        return chamberId;
    }

    public void recordAlert(String alertMessage) {
        alertsCount.incrementAndGet();
        if (alertMessage != null) {
            lastAlert.set(alertMessage);
        }
    }

    public void recordAction(String actionMessage) {
        mitigationActionsCount.incrementAndGet();
        if (actionMessage != null) {
            lastAction.set(actionMessage);
        }
    }

    public ChamberSummarySnapshot getSnapshot() {
        return new ChamberSummarySnapshot(
                alertsCount.get(),
                mitigationActionsCount.get(),
                lastAction.get(),
                lastAlert.get()
        );
    }
}
