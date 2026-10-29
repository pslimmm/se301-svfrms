package org.example.svfrms.domain.sensor;

import org.example.svfrms.domain.chamber.ChamberId;

import java.time.Instant;
import java.util.Objects;

/** Immutable reading emitted by one sensor. */
public record SensorReading(ChamberId chamberId, String crop, SensorType sensorType, double value, Instant timestamp) {
    public SensorReading {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(crop, "crop");
        Objects.requireNonNull(sensorType, "sensorType");
        Objects.requireNonNull(timestamp, "timestamp");
        if (crop.isBlank()) {
            throw new IllegalArgumentException("Crop name must not be blank");
        }
    }

    public SensorReading(SensorReading source) {
        this(source.chamberId, source.crop, source.sensorType, source.value, source.timestamp);
    }
}
