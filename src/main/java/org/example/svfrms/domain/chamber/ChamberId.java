package org.example.svfrms.domain.chamber;

import java.util.Objects;

/** Stable identifier for a growing chamber. */
public record ChamberId(String value) {
    public ChamberId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Chamber id must not be blank");
        }
    }
}
