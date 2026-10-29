package org.example.svfrms.domain.sensor;

import org.example.svfrms.domain.chamber.ChamberId;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain event representing a single telemetry observation from a chamber sensor.
 */
public record SensorReading(
        ChamberId chamberId,
        String crop,
        SensorType sensorType,
        double value,
        Instant timestamp
) {
    public SensorReading {
        Objects.requireNonNull(chamberId, "chamberId must not be null");
        Objects.requireNonNull(crop, "crop must not be null");
        Objects.requireNonNull(sensorType, "sensorType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
