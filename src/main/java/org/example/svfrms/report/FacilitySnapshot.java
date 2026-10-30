package org.example.svfrms.report;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.example.svfrms.chamber.ChamberSummarySnapshot;
import org.example.svfrms.model.Alert;

/**
 * Immutable picture of the whole facility, taken once and handed to report generators. Because reports
 * only read this copy, they never need any locks and every number in one report is consistent.
 */
public record FacilitySnapshot(
        String timestamp,
        long generatedAlerts,
        List<Alert> activeAlerts,
        List<Alert> archivedAlerts,
        long waterLitres,
        long energyKwh,
        List<String> reportLog,
        SortedMap<String, ChamberSummarySnapshot> chambers,
        Map<String, Long> deviceActivations) {

    public long activations(String deviceName) {
        return deviceActivations.getOrDefault(deviceName, 0L);
    }
}
