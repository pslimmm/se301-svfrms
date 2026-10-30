package org.example;

/** Child-JVM fixture: relative archive paths resolve inside the test's temporary directory. */
public final class ArchiveReportProbe {
    private ArchiveReportProbe() {}

    public static void main(String[] args) {
        FarmApplication app = new FarmApplication();
        if (args[0].equals("populated")) {
            app.processSensor("CH2", "Tomato", "Temperature", 29);
            app.processSensor("CH10", "Strawberry", "Temperature", 17);
            app.activateControl("CH2", "Cooling Fan");
            app.handleEvent("TRACK_ALERT", "CH2", "", "Bok choy \u83dc");
            app.handleEvent("CREATE_ALERT", "CH2", "Bok Choy", "Bok choy \u83dc");
            app.irrigationCycle();
            app.feedingCycle();
            app.archiveAlerts();
            app.generateDailyReport();
        }
        app.saveArchiveReport();
    }
}
