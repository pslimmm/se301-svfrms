package org.example.svfrms.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The five kinds of sensor in a growing chamber.
 *
 * <p>The legacy code passed these around as raw strings ("Temperature", "Soil", ...), so a typo
 * compiled fine and silently did nothing. An enum makes the set of sensors explicit.
 */
public enum SensorType {
    TEMPERATURE("Temperature", "temperature", "Temp"),
    HUMIDITY("Humidity", "humidity", "Humidity"),
    SOIL_MOISTURE("Soil", "soil", "Soil Moisture"),
    LIGHT("Light", "light", "Light Level"),
    NUTRIENT("Nutrient", "nutrient", "Nutrient Level");

    private final String label;
    private final String configKey;
    private final String alertName;

    SensorType(String label, String configKey, String alertName) {
        this.label = label;
        this.configKey = configKey;
        this.alertName = alertName;
    }

    /** Name used in the sensor log, identical to the legacy string. */
    public String label() {
        return label;
    }

    /** Name used in the .properties configuration files. */
    public String configKey() {
        return configKey;
    }

    /** Wording used in alert messages, e.g. "CH1 Tomato High Temp". */
    public String alertName() {
        return alertName;
    }

    public static Optional<SensorType> fromLabel(String label) {
        return Arrays.stream(values()).filter(t -> t.label.equals(label)).findFirst();
    }

    public static Optional<SensorType> fromConfigKey(String key) {
        return Arrays.stream(values()).filter(t -> t.configKey.equals(key)).findFirst();
    }
}
