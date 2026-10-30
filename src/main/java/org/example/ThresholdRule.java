package org.example;

import java.util.Objects;
import java.util.List;

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

    /** One boundary per direction/sensor in a scope; crop and global scopes remain additive. */
    static void validateRules(List<ThresholdRule> rules) {
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                ThresholdRule left = rules.get(i);
                ThresholdRule right = rules.get(j);
                if (left.sensor == right.sensor && left.direction == right.direction) {
                    throw new IllegalArgumentException("Duplicate boundary: " + left.sensor + " " + left.direction);
                }
            }
        }
        validateRanges(rules, rules);
    }

    /** Opposing bounds must leave a nonzero acceptable interval, including across scopes. */
    static void validateRanges(List<ThresholdRule> first, List<ThresholdRule> second) {
        for (ThresholdRule left : first) {
            for (ThresholdRule right : second) {
                if (left.sensor != right.sensor || left.direction == right.direction) continue;
                double lower = left.direction == Direction.BELOW ? left.threshold : right.threshold;
                double upper = left.direction == Direction.ABOVE ? left.threshold : right.threshold;
                if (lower >= upper) {
                    throw new IllegalArgumentException("Lower bound must be less than upper bound: " + left.sensor);
                }
            }
        }
    }
}
