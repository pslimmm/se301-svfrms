package org.example.svfrms.chamber;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.example.svfrms.alert.AlertListener;
import org.example.svfrms.device.DeviceActivation;
import org.example.svfrms.device.DeviceListener;
import org.example.svfrms.model.Alert;

/**
 * Keeps a {@link ChamberSummary} per chamber by listening to alerts and device activations
 * (Observer). This replaces the legacy "TRACK_ALERT" and "TRACK_ACTION" branches of
 * {@code handleEvent}. As in the legacy code, a chamber only gets a summary once something
 * happens in it.
 */
public final class ChamberSummaryTracker implements AlertListener, DeviceListener {

    private final Map<String, ChamberSummary> summaries = new ConcurrentHashMap<>();

    @Override
    public void onAlert(Alert alert) {
        summaryFor(alert.chamberId()).recordAlert(alert.message());
    }

    @Override
    public void onDeviceActivated(DeviceActivation activation) {
        summaryFor(activation.chamberId()).recordAction(activation.deviceName() + " Activated");
    }

    /** Snapshot of every chamber, sorted by chamber id like the legacy TreeSet. */
    public SortedMap<String, ChamberSummarySnapshot> snapshot() {
        SortedMap<String, ChamberSummarySnapshot> result = new TreeMap<>();
        summaries.forEach((chamber, summary) -> result.put(chamber, summary.snapshot()));
        return result;
    }

    private ChamberSummary summaryFor(String chamberId) {
        return summaries.computeIfAbsent(chamberId, id -> new ChamberSummary());
    }
}
