package org.example.svfrms.domain.crop;

/**
 * Immutable value object defining acceptable operating boundaries [min, max] for an environmental dimension.
 */
public record ThresholdRange(double min, double max) {

    public ThresholdRange {
        if (min > max) {
            throw new IllegalArgumentException(
                    "Invalid range: min (" + min + ") cannot be greater than max (" + max + ")");
        }
    }

    public static ThresholdRange of(double min, double max) {
        return new ThresholdRange(min, max);
    }

    public boolean isBelow(double value) {
        return value < min;
    }

    public boolean isAbove(double value) {
        return value > max;
    }

    public boolean isWithin(double value) {
        return value >= min && value <= max;
    }
}
