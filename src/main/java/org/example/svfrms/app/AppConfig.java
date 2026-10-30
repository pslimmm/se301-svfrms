package org.example.svfrms.app;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * All the settings for one run.
 *
 * @param chambers    chamber id to crop name, in the order they should run
 * @param extraCrops  optional file with additional (or replacement) crop profiles
 * @param seed        optional random seed for a repeatable run
 * @param sequential  run chambers one after another on one thread (deterministic output)
 * @param archiveDir  where daily_report.txt is written
 * @param allReports  also print the optional water/energy/nutrient/crop reports
 * @param cycles      sensing cycles per chamber (legacy: 20)
 */
public record AppConfig(Map<String, String> chambers, Optional<Path> extraCrops, Optional<Long> seed,
                        boolean sequential, Path archiveDir, boolean allReports, int cycles) {

    public static final int LEGACY_CYCLES = 20;

    public AppConfig {
        chambers = new LinkedHashMap<>(chambers);
        if (cycles < 0) {
            throw new IllegalArgumentException("cycles cannot be negative");
        }
    }

    public Path archiveFile() {
        return archiveDir.resolve("daily_report.txt");
    }
}
