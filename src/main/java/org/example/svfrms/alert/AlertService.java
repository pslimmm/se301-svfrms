package org.example.svfrms.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.example.svfrms.model.Alert;

/**
 * Owns the active and archived alert lists and the alert counters, and tells listeners about new alerts
 * (Observer subject).
 *
 * <p><b>Concurrency fixes (C2, C3, C6, C7):</b>
 * <ul>
 *   <li>The lists and counters are only touched while holding {@code lock}, so no updates are lost and
 *       readers always see a consistent picture (the legacy report locks were never taken by writers).</li>
 *   <li>One lock instead of the legacy {@code alertLock} + {@code reportLock} pair, and it is never held
 *       while calling other objects. Listeners are notified after the lock is released. So this class
 *       can never take part in a lock-ordering deadlock.</li>
 * </ul>
 */
public final class AlertService {

    private final Object lock = new Object();
    private final List<Alert> active = new ArrayList<>();
    private final List<Alert> archived = new ArrayList<>();
    private long totalGenerated;
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }

    public void raise(Alert alert) {
        synchronized (lock) {
            active.add(alert);
            totalGenerated++;
        }
        // notify outside the lock: we never call "foreign" code while holding a lock
        for (AlertListener listener : listeners) {
            listener.onAlert(alert);
        }
    }

    /** Moves every active alert to the archive and returns how many were moved. */
    public int archiveActive() {
        synchronized (lock) {
            int moved = active.size();
            archived.addAll(active);
            active.clear();
            return moved;
        }
    }

    /** Consistent copy of counters and lists taken under one lock. */
    public AlertSnapshot snapshot() {
        synchronized (lock) {
            return new AlertSnapshot(totalGenerated, List.copyOf(active), List.copyOf(archived));
        }
    }
}
