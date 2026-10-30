package org.example;

import java.util.List;

import static org.example.DeviceType.*;
import static org.example.SensorType.*;
import static org.example.ThresholdRule.Direction.*;

/** Opt-in extension example. Thresholds and charges are synthetic demonstration inputs. */
public final class CropExtensionDemo {
    private CropExtensionDemo() {}

    /** Keeps the default profiles/assignments and adds Kale in CH5 through configuration only. */
    public static CropCatalog catalog() {
        CropProfile kale = new CropProfile("Kale", List.of(
            new ThresholdRule(TEMPERATURE, BELOW, 16, "Low Temp", null, 0, 0),
            new ThresholdRule(TEMPERATURE, ABOVE, 24, "High Temp", COOLING_FAN, 0, 3),
            new ThresholdRule(SOIL, BELOW, 40, "Low Soil Moisture", WATER_PUMP, 10, 0)));
        return CropCatalog.legacyDefaults().withProfile(kale).withAssignment("CH5", "Kale");
    }

    public static void main(String[] args) throws InterruptedException {
        new FarmApplication(catalog()).run();
    }
}
