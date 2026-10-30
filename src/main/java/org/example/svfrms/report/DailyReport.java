package org.example.svfrms.report;

import java.util.List;

/** The daily report printed to the console (legacy {@code generateDailyReport()}). */
public final class DailyReport extends TemplateReport {

    @Override
    protected String title() {
        return "DAILY REPORT";
    }

    @Override
    protected void addBody(FacilitySnapshot snapshot, List<String> lines) {
        addTotals(snapshot, lines);
    }
}
