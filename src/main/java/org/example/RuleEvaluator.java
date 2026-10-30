package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure evaluation: no state mutation, clock, output, queue, or crop-name branching. */
public final class RuleEvaluator {
    private final CropCatalog catalog;

    /** Null control preserves alert-only rules, including low-temperature energy charges. */
    public record Effect(String alert, ControlRequest control, int waterUsage, int energyUsage) {}

    public RuleEvaluator(CropCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public List<Effect> evaluate(String chamber, String crop, String sensor, double value) {
        SensorType type = SensorType.fromLegacyName(sensor).orElse(null);
        if (type == null) return List.of();
        List<Effect> effects = new ArrayList<>();
        catalog.profile(crop).ifPresent(profile -> collect(effects, profile.rules(),
            chamber, chamber + " " + crop, type, value));
        collect(effects, catalog.globalRules(), chamber, chamber, type, value);
        return List.copyOf(effects);
    }

    private static void collect(List<Effect> effects, List<ThresholdRule> rules, String chamber,
                                String prefix, SensorType sensor, double value) {
        for (ThresholdRule rule : rules) {
            if (rule.matches(sensor, value)) {
                ControlRequest control = rule.control() == null ? null : new ControlRequest(chamber, rule.control());
                effects.add(new Effect(prefix + " " + rule.alertSuffix(), control, rule.waterUsage(), rule.energyUsage()));
            }
        }
    }
}
