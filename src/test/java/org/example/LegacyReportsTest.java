package org.example;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LegacyReportsTest extends LegacyTestSupport {
    @TempDir Path temporaryDirectory;

    @Test
    void archiveMovesAlertsWithoutResettingLifetimeTotalsOrSummaries() {
        app.processSensor("CH1", "Tomato", "Temperature", 20);
        app.processSensor("CH1", "Tomato", "Temperature", 21);
        List<String> alerts = List.copyOf(app.snapshot().alerts());
        clearOutput();

        app.archiveAlerts();
        app.archiveAlerts();

        assertTrue(app.snapshot().alerts().isEmpty());
        assertEquals(alerts, app.snapshot().archivedAlerts());
        assertEquals(0, app.snapshot().totalAlerts());
        assertEquals(2, app.snapshot().totalAlertsGenerated());
        assertEquals(2, app.getChamberSummary("CH1").alerts());
        assertEquals("CH1 Tomato Low Temp", app.getChamberSummary("CH1").lastAlert());
        assertEquals(30, app.snapshot().totalEnergyUsage());
        assertEquals(List.of("Archived 2 alerts", "No alerts to archive"), app.snapshot().reports());
        assertEquals("Archiving alerts... 2 moved to archive.\n"
            + "Archiving alerts... no active alerts to archive.\n", output());

        app.processSensor("CH1", "Tomato", "Temperature", 20);
        app.archiveAlerts();
        assertEquals(3, app.snapshot().archivedAlerts().size(), "later archive appends instead of replacing");
        assertEquals(3, app.snapshot().totalAlertsGenerated());
        assertEquals(0, app.snapshot().totalAlerts());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportSupportsEitherLegacyArchiveOrdering(boolean archiveFirst) {
        app.processSensor("CH1", "Tomato", "Temperature", 20);
        app.irrigationCycle();
        if (archiveFirst) {
            app.archiveAlerts();
        }
        clearOutput();

        app.generateDailyReport();

        assertEquals("===== DAILY REPORT =====\nGenerated Alerts: 1\nActive Alerts: "
            + (archiveFirst ? 0 : 1) + "\nWater Usage: 100 L\nEnergy Usage: 15 kWh\n", output());
        if (!archiveFirst) {
            app.archiveAlerts();
        }
        assertEquals(archiveFirst ? List.of("Archived 1 alerts", "Daily Report")
            : List.of("Daily Report", "Archived 1 alerts"), app.snapshot().reports());
        assertEquals(0, app.snapshot().totalAlerts());
        assertEquals(1, app.snapshot().totalAlertsGenerated());
    }

    @Test
    void displayHelpersPrintStoredMessagesInOrder() {
        app.processSensor("CH1", "Tomato", "Temperature", 20);
        app.processSensor("CH1", "Tomato", "Temperature", 21);
        clearOutput();
        app.showAlerts();
        assertEquals("CH1 Tomato Low Temp\nCH1 Tomato Low Temp\n", output());
        clearOutput();
        app.showLogs();
        assertEquals(String.join("\n", app.snapshot().sensorLogs()) + "\n", output());
    }

    @Test
    void eventHelpersKeepLoggingAndTrackingSeparate() {
        app.handleEvent("ALERT_LOG", "CH1", "Tomato", "display only");
        app.handleEvent("CONTROL_LOG", "CH1", "", "control only");
        app.handleEvent("UNKNOWN", "CH1", "Tomato", "ignored");
        assertTrue(app.snapshot().alerts().isEmpty());
        assertTrue(app.snapshot().chamberSummaries().isEmpty());
        assertEquals(0, app.snapshot().totalAlertsGenerated());
        assertEquals("[CH1][Tomato][ALERT] display only\n"
            + "\t\u001B[32m[CH1][CONTROL] control only\u001B[0m\n", output());

        app.handleEvent("TRACK_ALERT", "CH1", "", "tracked alert");
        app.handleEvent("TRACK_ACTION", "CH1", "", "tracked action");
        assertTrue(app.snapshot().alerts().isEmpty());
        assertEquals(0, app.snapshot().totalAlertsGenerated());
        var summary = app.getChamberSummary("CH1");
        assertEquals(1, summary.alerts());
        assertEquals("tracked alert", summary.lastAlert());
        assertEquals(1, summary.mitigationActions());
        assertEquals("tracked action", summary.lastAction());
        app.handleEvent("CREATE_ALERT", "CH1", "Tomato", "created alert");
        assertEquals(List.of("created alert"), app.snapshot().alerts());
        assertEquals(1, app.snapshot().totalAlerts());
        assertEquals(1, app.snapshot().totalAlertsGenerated());
        assertEquals("tracked alert", app.getChamberSummary("CH1").lastAlert(), "creation alone does not track a summary");
    }

    @Test
    void savesUtf8ReportWithSortedSummariesAndTruncatesPreviousContents() throws Exception {
        Path report = temporaryDirectory.resolve("archive/daily_report.txt");
        assertFalse(Files.exists(report.getParent()));
        String stdout = runArchiveProbe("populated");
        assertTrue(stdout.contains("Archive report saved to: " + report.toAbsolutePath()));
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        assertEquals("===== DAILY REPORT =====", lines.getFirst());
        assertTrue(lines.get(1).startsWith("Timestamp: "));
        assertFalse(lines.get(1).substring("Timestamp: ".length()).isBlank());
        assertEquals(List.of(
            "Generated Alerts: 3", "Active Alerts: 0", "Water Usage: 100 L", "Energy Usage: 45 kWh",
            "Archived alerts: 3", "---", "Archived 3 alerts", "Daily Report",
            "--- chamber wise summary ---",
            "CH10 -> Alerts=1, Actions=0, Last Action=No action, Last Alert=CH10 Strawberry Low Temp",
            "CH2 -> Alerts=2, Actions=1, Last Action=Cooling Fan Activated, Last Alert=Bok choy \u83dc",
            "--- archived alerts ---", "CH2 Tomato High Temp", "CH10 Strawberry Low Temp", "Bok choy \u83dc"
        ), lines.subList(2, lines.size()));

        runArchiveProbe("empty");
        List<String> overwritten = Files.readAllLines(report, StandardCharsets.UTF_8);
        assertEquals(8, overwritten.size());
        assertEquals(List.of("Generated Alerts: 0", "Active Alerts: 0", "Water Usage: 0 L",
            "Energy Usage: 0 kWh", "Archived alerts: 0", "---"), overwritten.subList(2, 8));
    }

    @Test
    void archiveFailurePrintsAnErrorAndLeavesExistingFileUntouched() throws Exception {
        Path blocker = temporaryDirectory.resolve("archive");
        Files.writeString(blocker, "do not overwrite", StandardCharsets.UTF_8);
        String stdout = runArchiveProbe("empty");
        assertTrue(stdout.startsWith("Could not save archive report: "));
        assertFalse(stdout.contains("Archive report saved to:"));
        assertEquals("do not overwrite", Files.readString(blocker));
    }

    private String runArchiveProbe(String scenario) throws Exception {
        // Changing user.dir does not reliably relocate java.nio paths; use a real child working directory.
        Path java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        String classpath = Path.of(SVFRMS.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            + File.pathSeparator
            + Path.of(ArchiveReportProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path stdout = temporaryDirectory.resolve("probe-output.txt");
        Process process = new ProcessBuilder(java.toString(), "-cp", classpath,
            ArchiveReportProbe.class.getName(), scenario)
            .directory(temporaryDirectory.toFile()).redirectErrorStream(true)
            .redirectOutput(stdout.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "archive probe must terminate");
            String output = Files.readString(stdout, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "archive probe must be cleaned up");
            }
        }
    }
}
