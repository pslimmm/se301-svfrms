package org.example.svfrms.service.alert;

import org.example.svfrms.domain.alert.AlertEvent;

import java.util.*;

/** Consistent point-in-time view of alert totals, active alerts, and archived alerts. */
public record AlertSnapshot(long generatedAlertCount, List<AlertEvent> activeAlerts,
                            List<AlertEvent> archivedAlerts) {
    public AlertSnapshot {
        if (generatedAlertCount < 0) {
            throw new IllegalArgumentException("generatedAlertCount must be non-negative");
        }
        activeAlerts = List.copyOf(Objects.requireNonNull(activeAlerts, "activeAlerts"));
        archivedAlerts = List.copyOf(Objects.requireNonNull(archivedAlerts, "archivedAlerts"));
    }
}
