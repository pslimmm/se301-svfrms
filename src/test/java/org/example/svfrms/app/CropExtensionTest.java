package org.example.svfrms.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.example.testsupport.ConsoleCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Brief section 4.5: adding and removing crops with no code changes. These tests go through the full
 * system exactly as {@code java -jar run.jar --chambers ... --crops ...} would.
 */
class CropExtensionTest {

    @TempDir
    Path dir;

    private String run(Map<String, String> chambers, Optional<Path> extraCrops, AtomicReference<Facility> out) {
        AppConfig config = new AppConfig(chambers, extraCrops, Optional.of(7L), true, dir, false, 20);
        return ConsoleCapture.capture(() -> {
            Facility facility = FacilityAssembler.assemble(config, System.out);
            out.set(facility);
            facility.simulation().run();
        });
    }

    @Test
    void theFourNewCropsRunWithoutAnyCodeChange() {
        Map<String, String> chambers = new LinkedHashMap<>();
        chambers.put("CH5", "Kale");
        chambers.put("CH6", "Bell Pepper");
        chambers.put("CH7", "Bok Choy");
        chambers.put("CH8", "Eggplant");
        String console = run(chambers, Optional.empty(), new AtomicReference<>());
        assertTrue(console.contains("[CH5][Kale][ALERT] CH5 Kale"));
        assertTrue(console.contains("[CH6][Bell Pepper][ALERT] CH6 Bell Pepper"));
        assertTrue(console.contains("[CH7][Bok Choy][ALERT] CH7 Bok Choy"));
        assertTrue(console.contains("[CH8][Eggplant][ALERT] CH8 Eggplant"));
    }

    @Test
    void aBrandNewCropCanBeAddedFromAFileAtRunTime() throws Exception {
        Path basil = dir.resolve("basil.properties");
        Files.writeString(basil, """
                basil.name=Basil
                basil.temperature.min=20
                basil.temperature.max=26
                """);
        String console = run(Map.of("CH9", "Basil"), Optional.of(basil), new AtomicReference<>());
        assertTrue(console.contains("[CH9][Basil][ALERT] CH9 Basil High Temp"));
    }

    @Test
    void removedCropNoLongerGetsCropAlerts() {
        AtomicReference<Facility> facility = new AtomicReference<>();
        AppConfig config = new AppConfig(Map.of("CH1", "Tomato"), Optional.empty(), Optional.of(7L), true, dir, false, 20);
        String console = ConsoleCapture.capture(() -> {
            Facility f = FacilityAssembler.assemble(config, System.out);
            facility.set(f);
            f.crops().remove("Tomato");
            f.simulation().run();
        });
        assertFalse(console.contains("Tomato High Temp"));
        assertTrue(console.contains("CH1 Low Light Level"), "facility-wide rules still apply");
    }
}
