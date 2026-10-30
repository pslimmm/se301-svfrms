package org.example.svfrms.model;

/**
 * An acceptable range for a reading. A missing bound is stored as infinity, so a range with only a
 * minimum (like Tomato soil moisture) never reports "too high".
 *
 * <p>The checks are strict (&lt; and &gt;), exactly like the legacy code: a value sitting on the
 * boundary is still fine.
 */
public record Range(double min, double max) {

    public Range {
        if (min > max) {
            throw new IllegalArgumentException("Range min " + min + " is greater than max " + max);
        }
    }

    public static Range between(double min, double max) {
        return new Range(min, max);
    }

    public static Range atLeast(double min) {
        return new Range(min, Double.POSITIVE_INFINITY);
    }

    public static Range atMost(double max) {
        return new Range(Double.NEGATIVE_INFINITY, max);
    }

    public boolean isBelow(double value) {
        return value < min;
    }

    public boolean isAbove(double value) {
        return value > max;
    }
}
