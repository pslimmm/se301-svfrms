package org.example.svfrms.service.alert;

import org.example.svfrms.domain.alert.AlertEvent;

import java.util.*;

/** Concurrent alert ingestion, immutable snapshots, and batch archival. */
public final class AlertManagementService {
    private final List<AlertEvent> activeAlerts = new ArrayList<>();
    private final List<AlertEvent> archivedAlerts = new ArrayList<>();
    private long generatedAlerts;

    public synchronized void publishAlert(AlertEvent alert) {
        activeAlerts.add(Objects.requireNonNull(alert, "alert"));
        generatedAlerts++;
    }

    public synchronized List<AlertEvent> getActiveAlertsSnapshot() {
        return List.copyOf(activeAlerts);
    }

    /** Returns alert counts and both stores at one archival/publish boundary. */
    public synchronized AlertSnapshot snapshot() {
        return new AlertSnapshot(generatedAlerts, activeAlerts, archivedAlerts);
    }

    /** Moves all active alerts into the archive and returns an immutable copy of the batch. */
    public synchronized List<AlertEvent> archiveAlerts() {
        List<AlertEvent> moved = List.copyOf(activeAlerts);
        archivedAlerts.addAll(moved);
        activeAlerts.clear();
        return moved;
    }

    public synchronized List<AlertEvent> getArchivedAlertsSnapshot() {
        return List.copyOf(archivedAlerts);
    }

    public synchronized long generatedAlertCount() {
        return generatedAlerts;
    }
}
