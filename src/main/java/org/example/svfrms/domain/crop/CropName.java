package org.example.svfrms.domain.crop;

import java.util.Locale;
import java.util.Objects;

/** Crop identifier with a stable case-insensitive registry key. */
public record CropName(String value) {
    public CropName {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Crop name must not be blank");
        }
    }

    public String key() {
        return value.toLowerCase(Locale.ROOT);
    }
}
