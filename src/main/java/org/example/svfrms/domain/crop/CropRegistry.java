package org.example.svfrms.domain.crop;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe catalog that permits crop profiles to be added and removed at runtime. */
public final class CropRegistry {
    private static final double OPEN_MAX = Double.MAX_VALUE;
    private final ConcurrentMap<String, CropProfile> profiles = new ConcurrentHashMap<>();

    public void register(CropProfile profile) {
        profiles.put(key(profile.name()), profile);
    }

    public boolean unregister(String name) {
        return profiles.remove(key(name)) != null;
    }

    public Optional<CropProfile> get(String name) {
        return Optional.ofNullable(profiles.get(key(name)));
    }

    public int size() {
        return profiles.size();
    }

    public static CropRegistry withStandardProfiles() {
        CropRegistry registry = new CropRegistry();
        ThresholdRange open = new ThresholdRange(0, OPEN_MAX);
        ThresholdRange lowLight = new ThresholdRange(500, OPEN_MAX);
        ThresholdRange lowNutrient = new ThresholdRange(40, OPEN_MAX);
        registry.register(profile("Tomato", new ThresholdRange(22, 28), new ThresholdRange(60, 80), new ThresholdRange(45, 60), lowLight, lowNutrient,
                new MitigationResourceCosts(10, 15, 20, 50)));
        registry.register(profile("Strawberry", new ThresholdRange(18, 24), new ThresholdRange(70, 85), open, lowLight, lowNutrient,
                new MitigationResourceCosts(10, 15, 25, 0)));
        registry.register(profile("Lettuce", new ThresholdRange(15, 21), open, open, lowLight, lowNutrient));
        registry.register(profile("Spinach", new ThresholdRange(10, 20), open, open, lowLight, lowNutrient));
        registry.register(profile("Chili", new ThresholdRange(24, 32), open, open, lowLight, lowNutrient));
        registry.register(profile("Cucumber", new ThresholdRange(18, 27), open, open, lowLight, lowNutrient));
        return registry;
    }

    private static CropProfile profile(String name, ThresholdRange temperature, ThresholdRange humidity,
                                       ThresholdRange soil, ThresholdRange light, ThresholdRange nutrient) {
        return new CropProfile(name, temperature, humidity, soil, light, nutrient);
    }

    private static CropProfile profile(String name, ThresholdRange temperature, ThresholdRange humidity,
                                       ThresholdRange soil, ThresholdRange light, ThresholdRange nutrient,
                                       MitigationResourceCosts costs) {
        return new CropProfile(name, temperature, humidity, soil, light, nutrient, costs);
    }

    private static String key(String name) {
        return new CropName(name).value().toLowerCase(Locale.ROOT);
    }
}
