package org.example.svfrms.app;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.example.svfrms.config.ConfigFile;

/** Parses the command line into an {@link AppConfig}. No arguments gives exactly the legacy run. */
public record CommandLineOptions(AppConfig config, boolean help, boolean listCrops) {

    public static final String USAGE = """
            Usage: java -jar run.jar [options]
              --chambers FILE   chamber layout (CHx=Crop per line), default: bundled chambers.properties
              --crops FILE      extra crop profiles to add to the bundled ones
              --seed N          fixed random seed for a repeatable run
              --sequential      run chambers one at a time (deterministic output)
              --cycles N        sensing cycles per chamber (default 20)
              --archive-dir DIR where daily_report.txt is written (default: archive)
              --all-reports     also print the weekly crop, water, energy and nutrient reports
              --list-crops      print the registered crop profiles and exit
              --help            show this message""";

    public static CommandLineOptions parse(String[] args) {
        Map<String, String> chambers = null;
        Optional<Path> extraCrops = Optional.empty();
        Optional<Long> seed = Optional.empty();
        boolean sequential = false;
        boolean allReports = false;
        boolean help = false;
        boolean listCrops = false;
        Path archiveDir = Path.of("archive");
        int cycles = AppConfig.LEGACY_CYCLES;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--chambers" -> chambers = ConfigFile.fromFile(Path.of(value(args, ++i, "--chambers")));
                case "--crops" -> extraCrops = Optional.of(Path.of(value(args, ++i, "--crops")));
                case "--seed" -> seed = Optional.of(number(value(args, ++i, "--seed"), "--seed"));
                case "--cycles" -> cycles = (int) number(value(args, ++i, "--cycles"), "--cycles");
                case "--archive-dir" -> archiveDir = Path.of(value(args, ++i, "--archive-dir"));
                case "--sequential" -> sequential = true;
                case "--all-reports" -> allReports = true;
                case "--list-crops" -> listCrops = true;
                case "--help", "-h" -> help = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (chambers == null) {
            chambers = ConfigFile.fromClasspath("chambers.properties");
        }
        AppConfig config = new AppConfig(chambers, extraCrops, seed, sequential, archiveDir, allReports, cycles);
        return new CommandLineOptions(config, help, listCrops);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static long number(String text, String option) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects a whole number but got '" + text + "'");
        }
    }
}
