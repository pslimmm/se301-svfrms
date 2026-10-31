package org.example.svfrms.domain.crop;

import java.util.*;
import org.example.svfrms.domain.sensor.SensorType;
import java.util.concurrent.*;

/** Thread-safe catalog that permits crop profiles to be added and removed at runtime. */
public final class CropRegistry {
    // Standard simulation thresholds; temperature is Celsius and humidity/soil moisture are percentages.
    private static final ThresholdRange TOMATO_TEMPERATURE_RANGE = new ThresholdRange(22, 28);
    private static final ThresholdRange TOMATO_HUMIDITY_RANGE = new ThresholdRange(60, 80);
    private static final ThresholdRange TOMATO_SOIL_MOISTURE_RANGE = new ThresholdRange(45, 60);
    private static final ThresholdRange STRAWBERRY_TEMPERATURE_RANGE = new ThresholdRange(18, 24);
    private static final ThresholdRange STRAWBERRY_HUMIDITY_RANGE = new ThresholdRange(70, 85);
    private static final ThresholdRange LETTUCE_TEMPERATURE_RANGE = new ThresholdRange(15, 21);
    private static final ThresholdRange SPINACH_TEMPERATURE_RANGE = new ThresholdRange(10, 20);
    private static final ThresholdRange CHILI_TEMPERATURE_RANGE = new ThresholdRange(24, 32);
    private static final ThresholdRange CUCUMBER_TEMPERATURE_RANGE = new ThresholdRange(18, 27);
    private static final long TOMATO_HIGH_TEMPERATURE_ENERGY_KWH = 10;
    private static final long TOMATO_LOW_TEMPERATURE_ENERGY_KWH = 15;
    private static final long TOMATO_LOW_HUMIDITY_WATER_LITRES = 20;
    private static final long TOMATO_LOW_SOIL_MOISTURE_WATER_LITRES = 50;
    private static final long STRAWBERRY_HIGH_TEMPERATURE_ENERGY_KWH = 10;
    private static final long STRAWBERRY_LOW_TEMPERATURE_ENERGY_KWH = 15;
    private static final long STRAWBERRY_LOW_HUMIDITY_WATER_LITRES = 25;

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
        ThresholdRange lowLight = new ThresholdRange(CropProfile.DEFAULT_MIN_LIGHT_LUX, OPEN_MAX);
        ThresholdRange lowNutrient = new ThresholdRange(CropProfile.DEFAULT_MIN_NUTRIENT_LEVEL_PERCENT, OPEN_MAX);
        registry.register(profile("Tomato", TOMATO_TEMPERATURE_RANGE, TOMATO_HUMIDITY_RANGE, TOMATO_SOIL_MOISTURE_RANGE, lowLight, lowNutrient,
                new MitigationResourceCosts(TOMATO_HIGH_TEMPERATURE_ENERGY_KWH, TOMATO_LOW_TEMPERATURE_ENERGY_KWH,
                        TOMATO_LOW_HUMIDITY_WATER_LITRES, TOMATO_LOW_SOIL_MOISTURE_WATER_LITRES), Set.of(SensorType.values())));
        registry.register(profile("Strawberry", STRAWBERRY_TEMPERATURE_RANGE, STRAWBERRY_HUMIDITY_RANGE, open, lowLight, lowNutrient,
                new MitigationResourceCosts(STRAWBERRY_HIGH_TEMPERATURE_ENERGY_KWH, STRAWBERRY_LOW_TEMPERATURE_ENERGY_KWH,
                        STRAWBERRY_LOW_HUMIDITY_WATER_LITRES, 0),
                Set.of(SensorType.TEMPERATURE, SensorType.HUMIDITY, SensorType.LIGHT, SensorType.NUTRIENT)));
        registry.register(profile("Lettuce", LETTUCE_TEMPERATURE_RANGE, open, open, lowLight, lowNutrient));
        registry.register(profile("Spinach", SPINACH_TEMPERATURE_RANGE, open, open, lowLight, lowNutrient));
        registry.register(profile("Chili", CHILI_TEMPERATURE_RANGE, open, open, lowLight, lowNutrient));
        registry.register(profile("Cucumber", CUCUMBER_TEMPERATURE_RANGE, open, open, lowLight, lowNutrient));
        return registry;
    }

    private static CropProfile profile(String name, ThresholdRange temperature, ThresholdRange humidity,
                                       ThresholdRange soil, ThresholdRange light, ThresholdRange nutrient) {
        return profile(name, temperature, humidity, soil, light, nutrient, MitigationResourceCosts.none(),
                Set.of(SensorType.TEMPERATURE, SensorType.LIGHT, SensorType.NUTRIENT));
    }

    private static CropProfile profile(String name, ThresholdRange temperature, ThresholdRange humidity,
                                       ThresholdRange soil, ThresholdRange light, ThresholdRange nutrient,
                                       MitigationResourceCosts costs, Set<SensorType> enabledSensors) {
        return new CropProfile(name, temperature, humidity, soil, light, nutrient, costs, enabledSensors);
    }

    private static String key(String name) {
        return new CropName(name).key();
    }
}
