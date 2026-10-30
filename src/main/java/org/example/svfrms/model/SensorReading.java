package org.example.svfrms.model;

import java.util.Objects;

/**
 * One immutable sensor measurement. Because records are immutable, a reading can be handed from
 * one thread to another without any locking.
 */
public record SensorReading(String chamberId, String crop, SensorType type, double value) {

    public SensorReading {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(crop, "crop");
        Objects.requireNonNull(type, "type");
    }

    /** NaN or infinite values mean the sensor is broken or unresponsive. */
    public boolean isValid() {
        return Double.isFinite(value);
    }
}
