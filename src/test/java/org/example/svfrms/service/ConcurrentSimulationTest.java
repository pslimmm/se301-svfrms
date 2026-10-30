package org.example.svfrms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.example.svfrms.alert.AlertSnapshot;
import org.example.svfrms.app.AppConfig;
import org.example.svfrms.app.Facility;
import org.example.svfrms.app.FacilityAssembler;
import org.example.svfrms.chamber.ChamberSummarySnapshot;
import org.example.svfrms.model.Alert;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real multi-threaded simulation (8 chambers in parallel plus the device worker) and checks
 * that every number adds up. Output order differs between runs, but the totals must not.
 */
class ConcurrentSimulationTest {

    private static final int CYCLES = 50;

    @TempDir
    Path archiveDir;

    @RepeatedTest(5)
    void totalsAddUpUnderRealConcurrency() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            Map<String, String> chambers = new LinkedHashMap<>();
            String[] crops = {"Tomato", "Strawberry", "Lettuce", "Chili", "Kale", "Bell Pepper", "Bok Choy", "Eggplant"};
            for (int i = 0; i < crops.length; i++) {
                chambers.put("CH" + (i + 1), crops[i]);
            }
            AppConfig config = new AppConfig(chambers, Optional.empty(), Optional.empty(), false, archiveDir, false, CYCLES);
            Facility facility = FacilityAssembler.assemble(config, new PrintStream(OutputStream.nullOutputStream()));

            facility.simulation().run();

            AlertSnapshot alerts = facility.alerts().snapshot();
            assertEquals(0, alerts.active().size(), "everything should be archived at the end");
            assertEquals(alerts.totalGenerated(), alerts.archived().size());

            int summaryAlerts = facility.summaries().snapshot().values().stream().mapToInt(ChamberSummarySnapshot::alerts).sum();
            assertEquals(alerts.totalGenerated(), summaryAlerts, "each alert counted once in the chamber summaries");

            long expectedWater = 2L * CYCLES * chambers.size();
            long expectedEnergy = (long) CYCLES * chambers.size();
            for (Alert alert : alerts.archived()) {
                var cost = facility.crops().find(alert.crop()).orElseThrow().costFor(alert.sensor(), alert.breach());
                expectedWater += cost.waterLitres();
                expectedEnergy += cost.energyKwh();
            }
            assertEquals(expectedWater, facility.usage().water(), "no lost water updates");
            assertEquals(expectedEnergy, facility.usage().energy(), "no lost energy updates");
        });
    }
}
