package org.example;

import java.util.Objects;

/** An enabled strict threshold and its consequences. A null control means alert/accounting only. */
public record ThresholdRule(SensorType sensor, Direction direction, double threshold,
                            String alertSuffix, DeviceType control, int waterUsage, int energyUsage) {
    public enum Direction { BELOW, ABOVE }

    public ThresholdRule {
        Objects.requireNonNull(sensor, "sensor");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(alertSuffix, "alertSuffix");
        if (!Double.isFinite(threshold)) throw new IllegalArgumentException("Threshold must be finite");
        if (alertSuffix.isBlank()) throw new IllegalArgumentException("Alert suffix must not be blank");
        if (waterUsage < 0 || energyUsage < 0) throw new IllegalArgumentException("Usage must not be negative");
    }

    public boolean matches(SensorType type, double value) {
        return sensor == type && (direction == Direction.BELOW ? value < threshold : value > threshold);
    }
}
