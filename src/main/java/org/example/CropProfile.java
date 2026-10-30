package org.example;

import java.util.List;
import java.util.Objects;

/** Only explicitly enabled rules belong here; descriptive specification ranges imply no new rules. */
public record CropProfile(String name, List<ThresholdRule> rules) {
    public CropProfile {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Crop name must not be blank");
        rules = List.copyOf(rules);
        ThresholdRule.validateRules(rules);
    }
}
