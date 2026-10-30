package org.example.legacy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.example.testsupport.ConsoleCapture;
import org.example.testsupport.GoldenScenario;
import org.example.testsupport.RunOutput;

/**
 * Drives the untouched legacy SVFRMS code with fixed inputs on a single thread.
 * After every sensor reading we drain the control queue ourselves (this is what the
 * legacy ControlThread does), which makes the legacy output fully repeatable.
 */
public final class LegacyHarness {

    private LegacyHarness() {
    }

    public static void reset() {
        SVFRMS.alerts.clear();
        SVFRMS.archivedAlerts.clear();
        SVFRMS.sensorLogs.clear();
        SVFRMS.reports.clear();
        SVFRMS.controlQueue.clear();
        SVFRMS.chamberStates.clear();
        SVFRMS.chamberSummaries.clear();
        SVFRMS.totalWaterUsage = 0;
        SVFRMS.totalEnergyUsage = 0;
        SVFRMS.totalAlerts = 0;
        SVFRMS.totalAlertsGenerated = 0;
        SVFRMS.controlThreadRunning = true;
    }

    /** Same body as legacy ChamberThread.run(), but with the control queue drained inline. */
    public static RunOutput runSeededScenario() {
        reset();
        SVFRMS.random = new Random(GoldenScenario.SEED);
        String console = ConsoleCapture.capture(() -> {
            for (Map.Entry<String, String> chamber : GoldenScenario.CHAMBERS.entrySet()) {
                String id = chamber.getKey();
                String crop = chamber.getValue();
                for (int i = 0; i < GoldenScenario.CYCLES; i++) {
                    sense(id, crop, "Temperature", 10 + SVFRMS.random.nextInt(30));
                    sense(id, crop, "Humidity", 40 + SVFRMS.random.nextInt(60));
                    sense(id, crop, "Soil", 20 + SVFRMS.random.nextInt(60));
                    sense(id, crop, "Light", 300 + SVFRMS.random.nextInt(900));
                    sense(id, crop, "Nutrient", 20 + SVFRMS.random.nextInt(80));
                    SVFRMS.totalWaterUsage += 2;
                    SVFRMS.totalEnergyUsage += 1;
                }
            }
            finish();
        });
        return collect(console);
    }

    public static RunOutput runBoundaryScenario() {
        reset();
        String console = ConsoleCapture.capture(() -> {
            for (GoldenScenario.Reading r : GoldenScenario.BOUNDARY_READINGS) {
                sense(r.chamber(), r.crop(), r.sensor(), r.value());
            }
            SVFRMS.irrigationCycle();
            SVFRMS.feedingCycle();
            SVFRMS.showAlerts();
            finish();
        });
        return collect(console);
    }

    private static void sense(String chamber, String crop, String sensor, double value) {
        SVFRMS.processSensor(chamber, crop, sensor, value);
        while (!SVFRMS.controlQueue.isEmpty()) {
            String[] parts = SVFRMS.controlQueue.remove(0).split("\\|");
            SVFRMS.activateControl(parts[0], parts[1]);
        }
    }

    /** The tail of legacy main(): archive first, then the daily report, then the file. */
    private static void finish() {
        SVFRMS.archiveAlerts();
        SVFRMS.generateDailyReport();
        System.out.println("System finished sensor processing.");
        SVFRMS.saveArchiveReport();
    }

    private static RunOutput collect(String console) {
        try {
            String log = SVFRMS.sensorLogs.stream()
                    .map(RunOutput::stripLogDate)
                    .collect(Collectors.joining("\n", "", "\n"));
            String report = ConsoleCapture.normalise(
                    Files.readString(SVFRMS.REPORT_FILE, StandardCharsets.UTF_8));
            return new RunOutput(RunOutput.maskSavedPath(console), log, RunOutput.maskTimestamp(report));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read legacy archive report", e);
        }
    }
}
