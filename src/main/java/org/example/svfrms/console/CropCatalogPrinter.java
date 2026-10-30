package org.example.svfrms.console;

import java.io.PrintStream;
import java.util.Map;

import org.example.svfrms.crop.CropProfile;
import org.example.svfrms.crop.CropRegistry;
import org.example.svfrms.crop.CropThreshold;
import org.example.svfrms.model.Range;
import org.example.svfrms.model.SensorType;

/** Prints every registered crop and its ranges (the {@code --list-crops} option). */
public final class CropCatalogPrinter {

    private final PrintStream out;

    public CropCatalogPrinter(PrintStream out) {
        this.out = out;
    }

    public void print(CropRegistry crops) {
        out.println("Registered crops:");
        for (CropProfile crop : crops.all()) {
            StringBuilder line = new StringBuilder("  " + crop.name());
            for (Map.Entry<SensorType, CropThreshold> entry : crop.thresholds().entrySet()) {
                line.append(" | ").append(entry.getKey().label()).append(' ').append(describe(entry.getValue().range()));
            }
            out.println(line);
        }
    }

    private static String describe(Range range) {
        boolean hasMin = Double.isFinite(range.min());
        boolean hasMax = Double.isFinite(range.max());
        if (hasMin && hasMax) {
            return range.min() + "-" + range.max();
        }
        return hasMin ? ">= " + range.min() : "<= " + range.max();
    }
}
