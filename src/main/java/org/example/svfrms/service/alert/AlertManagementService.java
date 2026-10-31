package org.example.svfrms.service.alert;

import org.example.svfrms.domain.alert.AlertEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/** Concurrent alert ingestion, immutable snapshots, and batch archival. */
public final class AlertManagementService {
    private final ConcurrentLinkedQueue<AlertEvent> activeAlerts = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AlertEvent> archivedAlerts = new ConcurrentLinkedQueue<>();
    private final LongAdder generatedAlerts = new LongAdder();

    public synchronized void publishAlert(AlertEvent alert) {
        activeAlerts.add(Objects.requireNonNull(alert, "alert"));
        generatedAlerts.increment();
    }

    public synchronized List<AlertEvent> getActiveAlertsSnapshot() {
        return List.copyOf(new ArrayList<>(activeAlerts));
    }

    /** Returns alert counts and both stores at one archival/publish boundary. */
    public synchronized AlertSnapshot snapshot() {
        return new AlertSnapshot(generatedAlerts.sum(), new ArrayList<>(activeAlerts), new ArrayList<>(archivedAlerts));
    }

    /** Moves every alert observed during this drain into the archive and returns the moved batch. */
    public synchronized List<AlertEvent> archiveAlerts() {
        List<AlertEvent> moved = new ArrayList<>();
        AlertEvent event;
        while ((event = activeAlerts.poll()) != null) {
            moved.add(event);
        }
        archivedAlerts.addAll(moved);
        return List.copyOf(moved);
    }

    public synchronized List<AlertEvent> getArchivedAlertsSnapshot() {
        return List.copyOf(new ArrayList<>(archivedAlerts));
    }

    public synchronized long generatedAlertCount() {
        return generatedAlerts.sum();
    }
}
