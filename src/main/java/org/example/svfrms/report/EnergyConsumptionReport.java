package org.example.svfrms.report;

import java.util.List;

/** Energy used and the powered devices that ran (brief 3.5d). Opt-in with {@code --all-reports}. */
public final class EnergyConsumptionReport extends TemplateReport {

    @Override
    protected String title() {
        return "ENERGY CONSUMPTION REPORT";
    }

    @Override
    protected void addBody(FacilitySnapshot snapshot, List<String> lines) {
        lines.add("Total Energy Usage: " + snapshot.energyKwh() + " kWh");
        lines.add("Cooling Fan activations: " + snapshot.activations("Cooling Fan"));
        lines.add("Grow Light activations: " + snapshot.activations("Grow Light"));
        lines.add("Heating Unit activations: " + snapshot.activations("Heating Unit"));
    }
}
