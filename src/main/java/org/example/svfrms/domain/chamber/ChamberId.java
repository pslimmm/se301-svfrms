package org.example.svfrms.domain.chamber;

import java.util.Objects;

/**
 * Strongly-typed value object representing a physical chamber identifier (e.g. "CH1", "CH2").
 */
public record ChamberId(String value) {

    public ChamberId {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ChamberId value cannot be null or blank");
        }
        value = value.trim();
    }

    public static ChamberId of(String value) {
        return new ChamberId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
