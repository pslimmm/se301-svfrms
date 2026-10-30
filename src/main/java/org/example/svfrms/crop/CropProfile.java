package org.example.svfrms.crop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.example.svfrms.model.Breach;
import org.example.svfrms.model.ResourceCost;
import org.example.svfrms.model.SensorType;

/**
 * Everything the system needs to know about one crop. It is pure data: adding Kale means adding a
 * few lines to {@code crops.properties}, not writing another {@code else if} block like in the
 * legacy {@code processSensor()} (Open/Closed Principle).
 *
 * <p>A sensor with no threshold has no crop rule. For example, the legacy code only checked
 * humidity for Tomato and Strawberry, so other crops simply have no humidity entry.
 */
public record CropProfile(String name, Map<SensorType, CropThreshold> thresholds) {

    public CropProfile {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Crop name cannot be blank");
        }
        EnumMap<SensorType, CropThreshold> copy = new EnumMap<>(SensorType.class);
        copy.putAll(thresholds);
        thresholds = Collections.unmodifiableMap(copy);
    }

    public Optional<CropThreshold> threshold(SensorType type) {
        return Optional.ofNullable(thresholds.get(type));
    }

    public ResourceCost costFor(SensorType type, Breach breach) {
        return threshold(type).map(t -> t.costFor(breach)).orElse(ResourceCost.NONE);
    }
}
