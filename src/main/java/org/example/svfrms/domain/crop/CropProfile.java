package org.example.svfrms.domain.crop;

import java.util.*;
import org.example.svfrms.domain.sensor.SensorType;

/** Immutable growing conditions for a crop. */
public record CropProfile(
        String name,
        ThresholdRange temperature,
        ThresholdRange humidity,
        ThresholdRange soilMoisture,
        ThresholdRange light,
        ThresholdRange nutrient,
        MitigationResourceCosts mitigationResourceCosts,
        Set<SensorType> enabledSensors) {

    public static final double DEFAULT_MIN_LIGHT_LUX = 500;
    public static final double DEFAULT_MIN_NUTRIENT_LEVEL_PERCENT = 40;

    public CropProfile(String name, ThresholdRange temperature, ThresholdRange humidity,
                       ThresholdRange soilMoisture, ThresholdRange light, ThresholdRange nutrient,
                       MitigationResourceCosts costs) {
        this(name, temperature, humidity, soilMoisture, light, nutrient, costs, Set.of(SensorType.values()));
    }

    public CropProfile(String name, ThresholdRange temperature, ThresholdRange humidity,
                       ThresholdRange soilMoisture, ThresholdRange light, ThresholdRange nutrient) {
        this(name, temperature, humidity, soilMoisture, light, nutrient, MitigationResourceCosts.none());
    }

    public CropProfile {
        name = new CropName(name).value();
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(humidity, "humidity");
        Objects.requireNonNull(soilMoisture, "soilMoisture");
        Objects.requireNonNull(light, "light");
        Objects.requireNonNull(nutrient, "nutrient");
        Objects.requireNonNull(mitigationResourceCosts, "mitigationResourceCosts");
        enabledSensors = Set.copyOf(enabledSensors);
    }
}
