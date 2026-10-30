package org.example.svfrms.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLineOptionsTest {

    @Test
    void noArgumentsMeansTheLegacyRun() {
        CommandLineOptions options = CommandLineOptions.parse(new String[0]);
        AppConfig config = options.config();
        assertEquals(List.of("CH1", "CH2", "CH3", "CH4"), List.copyOf(config.chambers().keySet()));
        assertEquals(List.of("Tomato", "Strawberry", "Lettuce", "Chili"), List.copyOf(config.chambers().values()));
        assertFalse(config.sequential());
        assertFalse(config.allReports());
        assertEquals(20, config.cycles());
        assertEquals(Path.of("archive", "daily_report.txt"), config.archiveFile());
        assertEquals(Optional.empty(), config.seed());
    }

    @Test
    void parsesEveryOption(@TempDir Path dir) throws Exception {
        Path chambers = dir.resolve("chambers.properties");
        Files.writeString(chambers, "# demo\nCH5=Kale\nCH1=Tomato\n");
        CommandLineOptions options = CommandLineOptions.parse(new String[] {
                "--chambers", chambers.toString(), "--crops", "extra.properties", "--seed", "42",
                "--sequential", "--cycles", "3", "--archive-dir", "out", "--all-reports", "--list-crops"});
        AppConfig config = options.config();
        assertEquals(List.of("CH5", "CH1"), List.copyOf(config.chambers().keySet()));
        assertEquals(Optional.of(Path.of("extra.properties")), config.extraCrops());
        assertEquals(Optional.of(42L), config.seed());
        assertTrue(config.sequential());
        assertTrue(config.allReports());
        assertTrue(options.listCrops());
        assertEquals(3, config.cycles());
        assertEquals(Path.of("out", "daily_report.txt"), config.archiveFile());
    }

    @Test
    void rejectsUnknownOrIncompleteOptions() {
        assertThrows(IllegalArgumentException.class, () -> CommandLineOptions.parse(new String[] {"--fast"}));
        assertThrows(IllegalArgumentException.class, () -> CommandLineOptions.parse(new String[] {"--seed"}));
        assertThrows(IllegalArgumentException.class, () -> CommandLineOptions.parse(new String[] {"--seed", "abc"}));
    }
}
