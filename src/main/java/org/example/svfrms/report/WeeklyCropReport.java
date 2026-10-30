package org.example.svfrms.report;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.example.svfrms.model.Alert;

/** Alerts per crop over the run (brief 3.5b). Opt-in with {@code --all-reports}. */
public final class WeeklyCropReport extends TemplateReport {

    @Override
    protected String title() {
        return "WEEKLY CROP REPORT";
    }

    @Override
    protected void addBody(FacilitySnapshot snapshot, List<String> lines) {
        Map<String, Integer> alertsPerCrop = new TreeMap<>();
        Stream.concat(snapshot.archivedAlerts().stream(), snapshot.activeAlerts().stream())
                .map(Alert::crop)
                .forEach(crop -> alertsPerCrop.merge(crop, 1, Integer::sum));
        if (alertsPerCrop.isEmpty()) {
            lines.add("No crop alerts recorded");
        }
        alertsPerCrop.forEach((crop, count) -> lines.add(crop + " -> Alerts=" + count));
    }
}
