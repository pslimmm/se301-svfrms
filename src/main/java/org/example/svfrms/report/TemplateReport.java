package org.example.svfrms.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Template Method: every report has the same skeleton (a "===== TITLE =====" banner followed by a body).
 * {@link #generate} fixes that skeleton, and subclasses only fill in {@link #title()} and
 * {@link #addBody}. The legacy code wrote the daily-report header and totals twice, once for the
 * console and once for the file. Here they are written once.
 */
public abstract class TemplateReport implements ReportGenerator {

    @Override
    public final List<String> generate(FacilitySnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("===== " + title() + " =====");
        addBody(snapshot, lines);
        return lines;
    }

    protected abstract String title();

    protected abstract void addBody(FacilitySnapshot snapshot, List<String> lines);

    /** The four totals shared by the console and file daily reports. */
    protected static void addTotals(FacilitySnapshot snapshot, List<String> lines) {
        lines.add("Generated Alerts: " + snapshot.generatedAlerts());
        lines.add("Active Alerts: " + snapshot.activeAlerts().size());
        lines.add("Water Usage: " + snapshot.waterLitres() + " L");
        lines.add("Energy Usage: " + snapshot.energyKwh() + " kWh");
    }
}
