package org.example.svfrms.domain.crop;

import java.util.Objects;

/** Crop identifier preserving the original case-sensitive, untrimmed lookup value. */
public record CropName(String value) {
    public CropName {
        Objects.requireNonNull(value, "value");
    }

    public String key() {
        return value;
    }
}
