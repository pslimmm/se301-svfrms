package org.example.svfrms.domain.sensor;

/**
 * Standard environmental sensor modalities supported by vertical farming chambers.
 */
public enum SensorType {
    TEMPERATURE("Temperature", "C"),
    HUMIDITY("Humidity", "%"),
    SOIL_MOISTURE("Soil", "%"),
    LIGHT("Light", "lux"),
    NUTRIENT("Nutrient", "%");

    private final String legacyKey;
    private final String unit;

    SensorType(String legacyKey, String unit) {
        this.legacyKey = legacyKey;
        this.unit = unit;
    }

    public String legacyKey() {
        return legacyKey;
    }

    public String unit() {
        return unit;
    }

    public static SensorType fromLegacyKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Sensor key cannot be null");
        }
        for (SensorType type : values()) {
            if (type.legacyKey.equalsIgnoreCase(key.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown sensor type: " + key);
    }
}
