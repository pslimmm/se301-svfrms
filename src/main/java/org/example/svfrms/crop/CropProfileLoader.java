package org.example.svfrms.crop;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.example.svfrms.model.Breach;
import org.example.svfrms.model.Range;
import org.example.svfrms.model.ResourceCost;
import org.example.svfrms.model.SensorType;

/**
 * Turns key=value entries into {@link CropProfile}s. Every crop is a block of keys sharing an id:
 *
 * <pre>
 * kale.name=Kale
 * kale.temperature.min=15
 * kale.temperature.max=24
 * kale.soil.min=50
 * kale.soil.low.water=40      (optional: litres used when soil moisture is low)
 * kale.temperature.high.energy=10   (optional: kWh used when temperature is high)
 * </pre>
 *
 * A crop is found by its {@code .name} key, so adding or removing a crop never touches Java code.
 */
public final class CropProfileLoader {

    private static final String NAME_SUFFIX = ".name";

    private CropProfileLoader() {
    }

    public static List<CropProfile> load(Map<String, String> entries, String source) {
        List<CropProfile> profiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getKey().endsWith(NAME_SUFFIX)) {
                String id = entry.getKey().substring(0, entry.getKey().length() - NAME_SUFFIX.length());
                profiles.add(loadOne(id, entry.getValue(), entries, source));
            }
        }
        return profiles;
    }

    private static CropProfile loadOne(String id, String name, Map<String, String> entries, String source) {
        Map<SensorType, CropThreshold> thresholds = new EnumMap<>(SensorType.class);
        for (SensorType type : SensorType.values()) {
            String prefix = id + "." + type.configKey();
            Double min = number(entries, prefix + ".min", source);
            Double max = number(entries, prefix + ".max", source);
            if (min == null && max == null) {
                continue;
            }
            Range range = new Range(
                    min == null ? Double.NEGATIVE_INFINITY : min,
                    max == null ? Double.POSITIVE_INFINITY : max);
            thresholds.put(type, new CropThreshold(range,
                    cost(entries, prefix + "." + Breach.LOW.configKey(), source),
                    cost(entries, prefix + "." + Breach.HIGH.configKey(), source)));
        }
        return new CropProfile(name, thresholds);
    }

    private static ResourceCost cost(Map<String, String> entries, String prefix, String source) {
        Double water = number(entries, prefix + ".water", source);
        Double energy = number(entries, prefix + ".energy", source);
        if (water == null && energy == null) {
            return ResourceCost.NONE;
        }
        return new ResourceCost(water == null ? 0 : water.intValue(), energy == null ? 0 : energy.intValue());
    }

    private static Double number(Map<String, String> entries, String key, String source) {
        String value = entries.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(source + ": '" + key + "' must be a number but was '" + value + "'", e);
        }
    }
}
