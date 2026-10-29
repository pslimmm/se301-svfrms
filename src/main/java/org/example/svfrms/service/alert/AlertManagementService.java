package org.example.svfrms.service.alert;

import org.example.svfrms.domain.alert.AlertEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe alert lifecycle management service.
 * Eliminates legacy phase race conditions and lost alerts via lock-free concurrent queues.
 */
public class AlertManagementService {

    private final ConcurrentLinkedQueue<AlertEvent> activeAlerts = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AlertEvent> archivedAlerts = new ConcurrentLinkedQueue<>();
    private final LongAdder totalAlertsIngested = new LongAdder();

    /**
     * Publishes a new alert into the active alert pool.
     *
     * @param alert the immutable alert event
     */
    public void publishAlert(AlertEvent alert) {
        Objects.requireNonNull(alert, "alert must not be null");
        activeAlerts.add(alert);
        totalAlertsIngested.increment();
    }

    /**
     * Returns an immutable point-in-time snapshot of active alerts.
     * Safe for concurrent report generation while alerts are actively arriving.
     */
    public List<AlertEvent> getActiveAlertsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(activeAlerts));
    }

    /**
     * Returns the current count of active unarchived alerts.
     */
    public int getActiveAlertsCount() {
        return activeAlerts.size();
    }

    /**
     * Atomically drains all active alerts into the archival store.
     * Preserves legacy terminal logging semantics.
     *
     * @return the list of alerts archived during this call
     */
    public List<AlertEvent> archiveAlerts() {
        System.out.println("Archiving alerts...");
        List<AlertEvent> drained = new ArrayList<>();
        AlertEvent event;
        while ((event = activeAlerts.poll()) != null) {
            drained.add(event);
            archivedAlerts.add(event);
        }
        System.out.println("Archived " + drained.size() + " alerts.");
        return Collections.unmodifiableList(drained);
    }

    /**
     * Returns an immutable snapshot of all historical archived alerts.
     */
    public List<AlertEvent> getAllArchivedAlerts() {
        return Collections.unmodifiableList(new ArrayList<>(archivedAlerts));
    }

    /**
     * Returns the total number of alerts ingested over the entire application lifecycle.
     */
    public long getTotalAlertsIngested() {
        return totalAlertsIngested.sum();
    }
}
