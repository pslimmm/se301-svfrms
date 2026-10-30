package org.example.svfrms.report;

import java.util.List;
import java.util.stream.Stream;

import org.example.svfrms.model.SensorType;

/** Nutrient deficiencies and injector runs (brief 3.5e). Opt-in with {@code --all-reports}. */
public final class NutrientConsumptionReport extends TemplateReport {

    @Override
    protected String title() {
        return "NUTRIENT CONSUMPTION REPORT";
    }

    @Override
    protected void addBody(FacilitySnapshot snapshot, List<String> lines) {
        long deficiencies = Stream.concat(snapshot.archivedAlerts().stream(), snapshot.activeAlerts().stream())
                .filter(alert -> alert.sensor() == SensorType.NUTRIENT)
                .count();
        lines.add("Nutrient Deficiency alerts: " + deficiencies);
        lines.add("Nutrient Injector activations: " + snapshot.activations("Nutrient Injector"));
    }
}
