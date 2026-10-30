package org.example.svfrms.report;

import java.util.List;

/** Water used and the devices that used it (brief 3.5c). Opt-in with {@code --all-reports}. */
public final class WaterUsageReport extends TemplateReport {

    @Override
    protected String title() {
        return "WATER USAGE REPORT";
    }

    @Override
    protected void addBody(FacilitySnapshot snapshot, List<String> lines) {
        lines.add("Total Water Usage: " + snapshot.waterLitres() + " L");
        lines.add("Water Pump activations: " + snapshot.activations("Water Pump"));
        lines.add("Humidifier activations: " + snapshot.activations("Humidifier"));
    }
}
