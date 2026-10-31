package org.example.svfrms.domain.crop;

/** Inclusive lower and upper bounds for one crop metric. */
public record ThresholdRange(double min, double max) {
    public ThresholdRange {
        if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
            throw new IllegalArgumentException("Threshold minimum must be less than or equal to maximum");
        }
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }
}
