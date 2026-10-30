package org.example;

import java.util.Objects;
import java.util.Optional;

/** Typed rule keys; external sensor names retain their exact, case-sensitive legacy spelling. */
public enum SensorType {
    TEMPERATURE("Temperature"), HUMIDITY("Humidity"), SOIL("Soil"), LIGHT("Light"), NUTRIENT("Nutrient");

    private final String legacyName;

    SensorType(String legacyName) {
        this.legacyName = legacyName;
    }

    public String legacyName() {
        return legacyName;
    }

    public static Optional<SensorType> fromLegacyName(String name) {
        Objects.requireNonNull(name, "sensor");
        for (SensorType type : values()) {
            if (type.legacyName.equals(name)) return Optional.of(type);
        }
        return Optional.empty();
    }
}
