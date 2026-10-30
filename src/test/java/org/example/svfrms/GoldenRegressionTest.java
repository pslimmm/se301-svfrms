package org.example.svfrms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.example.svfrms.app.AppConfig;
import org.example.svfrms.app.Facility;
import org.example.svfrms.app.FacilityAssembler;
import org.example.testsupport.ConsoleCapture;
import org.example.testsupport.GoldenScenario;
import org.example.testsupport.RunOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The key "behaviour preserved" proof: the refactored system, given the same inputs as the legacy code,
 * must produce byte-for-byte the same console output, sensor log and archive report (golden files
 * recorded from the legacy code by LegacyCharacterizationTest).
 */
class GoldenRegressionTest {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    @TempDir
    Path archiveDir;

    @Test
    void seededRunMatchesLegacyOutput() throws IOException {
        AppConfig config = new AppConfig(GoldenScenario.CHAMBERS, Optional.empty(), Optional.of(GoldenScenario.SEED),
                true, archiveDir, false, GoldenScenario.CYCLES);
        AtomicReference<Facility> facility = new AtomicReference<>();
        String console = ConsoleCapture.capture(() -> {
            facility.set(FacilityAssembler.assemble(config, System.out));
            facility.get().simulation().run();
        });
        assertMatchesGolden("seeded", collect(console, facility.get(), config));
    }

    @Test
    void boundaryReadingsMatchLegacyOutput() throws IOException {
        AppConfig config = new AppConfig(GoldenScenario.CHAMBERS, Optional.empty(), Optional.empty(),
                true, archiveDir, false, 0);
        AtomicReference<Facility> facility = new AtomicReference<>();
        String console = ConsoleCapture.capture(() -> {
            Facility f = FacilityAssembler.assemble(config, System.out);
            facility.set(f);
            for (GoldenScenario.Reading r : GoldenScenario.BOUNDARY_READINGS) {
                f.sensors().publish(r.chamber(), r.crop(), r.sensor(), r.value());
            }
            f.usage().irrigationCycle();
            f.usage().feedingCycle();
            f.views().showAlerts();
            f.endOfDay().run();
        });
        assertMatchesGolden("boundary", collect(console, facility.get(), config));
    }

    private static RunOutput collect(String console, Facility facility, AppConfig config) throws IOException {
        String log = facility.sensorLog().entries().stream()
                .map(RunOutput::stripLogDate)
                .collect(Collectors.joining("\n", "", "\n"));
        String report = ConsoleCapture.normalise(Files.readString(config.archiveFile(), StandardCharsets.UTF_8));
        return new RunOutput(RunOutput.maskSavedPath(console), log, RunOutput.maskTimestamp(report));
    }

    private static void assertMatchesGolden(String name, RunOutput actual) throws IOException {
        assertEquals(golden(name + "-console.txt"), actual.console(), "console output changed");
        assertEquals(golden(name + "-sensor-log.txt"), actual.sensorLog(), "sensor log changed");
        assertEquals(golden(name + "-archive-report.txt"), actual.archiveReport(), "archive report changed");
    }

    private static String golden(String file) throws IOException {
        return ConsoleCapture.normalise(Files.readString(GOLDEN_DIR.resolve(file), StandardCharsets.UTF_8));
    }
}
