package org.example;

import java.util.Objects;

public record SensorReading(String chamber, String crop, SensorType sensor, double value) {
    public SensorReading {
        Objects.requireNonNull(chamber);
        Objects.requireNonNull(crop);
        Objects.requireNonNull(sensor);
    }
}
