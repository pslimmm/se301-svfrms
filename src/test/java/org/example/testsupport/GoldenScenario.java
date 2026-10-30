package org.example.testsupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed inputs used for the characterization (golden file) tests.
 * Both the legacy code and the refactored code are driven with exactly these inputs,
 * one chamber after another on a single thread, so the output is deterministic.
 */
public final class GoldenScenario {

    public static final long SEED = 42L;
    public static final int CYCLES = 20;

    /** Covers every crop the legacy code knows about, not just the four used in main(). */
    public static final Map<String, String> CHAMBERS = new LinkedHashMap<>();

    static {
        CHAMBERS.put("CH1", "Tomato");
        CHAMBERS.put("CH2", "Strawberry");
        CHAMBERS.put("CH3", "Lettuce");
        CHAMBERS.put("CH4", "Chili");
        CHAMBERS.put("CH5", "Spinach");
        CHAMBERS.put("CH6", "Cucumber");
    }

    /** Hand-picked readings that sit exactly on, just inside and just outside each threshold. */
    public record Reading(String chamber, String crop, String sensor, double value) {
    }

    public static final List<Reading> BOUNDARY_READINGS = List.of(
            new Reading("CH1", "Tomato", "Temperature", 28),
            new Reading("CH1", "Tomato", "Temperature", 28.5),
            new Reading("CH1", "Tomato", "Temperature", 22),
            new Reading("CH1", "Tomato", "Temperature", 21.9),
            new Reading("CH1", "Tomato", "Humidity", 80),
            new Reading("CH1", "Tomato", "Humidity", 81),
            new Reading("CH1", "Tomato", "Humidity", 60),
            new Reading("CH1", "Tomato", "Humidity", 59),
            new Reading("CH1", "Tomato", "Soil", 45),
            new Reading("CH1", "Tomato", "Soil", 44),
            new Reading("CH1", "Tomato", "Soil", 95),
            new Reading("CH1", "Tomato", "Light", 500),
            new Reading("CH1", "Tomato", "Light", 499),
            new Reading("CH1", "Tomato", "Light", 950),
            new Reading("CH1", "Tomato", "Nutrient", 40),
            new Reading("CH1", "Tomato", "Nutrient", 39),
            new Reading("CH1", "Tomato", "Nutrient", 95),
            new Reading("CH2", "Strawberry", "Humidity", 86),
            new Reading("CH2", "Strawberry", "Humidity", 69),
            new Reading("CH2", "Strawberry", "Soil", 10),
            new Reading("CH3", "Lettuce", "Humidity", 10),
            new Reading("CH3", "Lettuce", "Temperature", 14),
            new Reading("CH5", "Spinach", "Temperature", 9),
            new Reading("CH5", "Spinach", "Temperature", 21),
            new Reading("CH6", "Cucumber", "Temperature", 17),
            new Reading("CH6", "Cucumber", "Temperature", 28),
            new Reading("CH4", "Chili", "Temperature", 33),
            new Reading("CH4", "Chili", "Temperature", 23),
            new Reading("CH7", "Unknown Crop", "Temperature", 99),
            new Reading("CH7", "Unknown Crop", "Light", 100),
            new Reading("CH7", "Unknown Crop", "Pressure", 5));

    private GoldenScenario() {
    }
}
