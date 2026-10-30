package org.example.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.example.testsupport.ConsoleCapture;
import org.example.testsupport.RunOutput;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests: they record what the legacy code does today.
 * Run with -Dgolden.update=true once to (re)write the golden files; afterwards the
 * test just confirms the legacy code still produces them, so the files can be trusted.
 */
class LegacyCharacterizationTest {

    static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    @Test
    void seededScenarioMatchesGoldenFiles() throws IOException {
        check("seeded", LegacyHarness.runSeededScenario());
    }

    @Test
    void boundaryScenarioMatchesGoldenFiles() throws IOException {
        check("boundary", LegacyHarness.runBoundaryScenario());
    }

    private static void check(String name, RunOutput output) throws IOException {
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN_DIR);
            write(name + "-console.txt", output.console());
            write(name + "-sensor-log.txt", output.sensorLog());
            write(name + "-archive-report.txt", output.archiveReport());
        }
        assertEquals(read(name + "-console.txt"), output.console());
        assertEquals(read(name + "-sensor-log.txt"), output.sensorLog());
        assertEquals(read(name + "-archive-report.txt"), output.archiveReport());
    }

    private static void write(String file, String content) throws IOException {
        Files.writeString(GOLDEN_DIR.resolve(file), content, StandardCharsets.UTF_8);
    }

    static String read(String file) throws IOException {
        // git may check the files out with CRLF on Windows
        return ConsoleCapture.normalise(Files.readString(GOLDEN_DIR.resolve(file), StandardCharsets.UTF_8));
    }
}
