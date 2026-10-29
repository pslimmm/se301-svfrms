package org.example.svfrms.domain.crop;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe dynamic registry managing crop profiles in accordance with the Open-Closed Principle (OCP).
 */
public class CropRegistry {

    private final ConcurrentHashMap<String, CropProfile> profiles = new ConcurrentHashMap<>();

    public void registerDefaults() {
        // 1. Tomato
        register(CropProfile.builder("Tomato")
                .temperature(22.0, 28.0)
                .humidity(60.0, 80.0)
                .soilMoisture(45.0, 60.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 100.0)
                .build());

        // 2. Strawberry
        register(CropProfile.builder("Strawberry")
                .temperature(18.0, 24.0)
                .humidity(70.0, 85.0)
                .soilMoisture(50.0, 65.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 100.0)
                .build());

        // 3. Lettuce
        register(CropProfile.builder("Lettuce")
                .temperature(15.0, 21.0)
                .humidity(65.0, 85.0)
                .soilMoisture(55.0, 75.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 100.0)
                .build());

        // 4. Spinach
        register(CropProfile.builder("Spinach")
                .temperature(10.0, 20.0)
                .humidity(60.0, 80.0)
                .soilMoisture(50.0, 70.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 100.0)
                .build());

        // 5. Chili
        register(CropProfile.builder("Chili")
                .temperature(24.0, 32.0)
                .humidity(60.0, 80.0)
                .soilMoisture(50.0, 70.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 100.0)
                .build());

        // 6. Cucumber
        register(CropProfile.builder("Cucumber")
                .temperature(18.0, 27.0)
                .humidity(60.0, 80.0)
                .soilMoisture(50.0, 70.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 100.0)
                .build());
    }

    public void register(CropProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("CropProfile must not be null");
        }
        profiles.put(profile.name(), profile);
    }

    public boolean unregister(String cropName) {
        if (cropName == null) {
            return false;
        }
        return profiles.remove(cropName) != null;
    }

    public Optional<CropProfile> get(String cropName) {
        if (cropName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(cropName));
    }

    public Collection<CropProfile> getAll() {
        return Collections.unmodifiableCollection(profiles.values());
    }
}
