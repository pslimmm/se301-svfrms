package org.example.svfrms.app;

import org.example.svfrms.console.CropCatalogPrinter;

/** Entry point for {@code java -jar run.jar}. Parses options, builds the facility, and runs it. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        CommandLineOptions options;
        try {
            options = CommandLineOptions.parse(args);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.err.println(CommandLineOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.println(CommandLineOptions.USAGE);
            return;
        }

        Facility facility = FacilityAssembler.assemble(options.config(), System.out);
        if (options.listCrops()) {
            new CropCatalogPrinter(System.out).print(facility.crops());
            return;
        }
        facility.simulation().run();
    }
}
