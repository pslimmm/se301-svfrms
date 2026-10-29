package org.example.svfrms.service.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.crop.ThresholdRange;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Evaluates crop ranges while keeping mitigation rules out of the registry. */
public final class DefaultCropThresholdStrategy implements CropThresholdStrategy {
    private final CropRegistry registry;
    private final Clock clock;

    public DefaultCropThresholdStrategy(CropRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    public DefaultCropThresholdStrategy(CropRegistry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EvaluationResult evaluate(SensorReading reading, CropProfile profile) {
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(profile, "profile");
        if (!reading.crop().equalsIgnoreCase(profile.name())) {
            throw new IllegalArgumentException("Reading crop does not match the supplied profile");
        }

        ThresholdRange range = rangeFor(profile, reading.sensorType());
        boolean below = reading.value() < range.min();
        boolean above = reading.value() > range.max();
        if (!below && !above) {
            return EvaluationResult.none();
        }

        ActuatorType actuator = null;
        long water = 0;
        long energy = 0;
        String message;
        if (reading.sensorType() == SensorType.TEMPERATURE) {
            message = reading.chamberId().value() + " " + profile.name() + (above ? " High Temp" : " Low Temp");
            if (above) {
                actuator = ActuatorType.COOLING_FAN;
                energy = profile.mitigationResourceCosts().highTemperatureEnergy();
            } else {
                energy = profile.mitigationResourceCosts().lowTemperatureEnergy();
            }
        } else if (reading.sensorType() == SensorType.HUMIDITY) {
            message = reading.chamberId().value() + " " + profile.name() + (above ? " High Humidity" : " Low Humidity");
            if (above) {
                actuator = ActuatorType.DEHUMIDIFIER;
            } else {
                actuator = ActuatorType.HUMIDIFIER;
                water = profile.mitigationResourceCosts().lowHumidityWater();
            }
        } else if (above) {
            // The legacy workflow only creates low moisture, light, and nutrient alerts.
            return EvaluationResult.none();
        } else if (reading.sensorType() == SensorType.SOIL_MOISTURE) {
            message = reading.chamberId().value() + " " + profile.name() + " Low Soil Moisture";
            actuator = ActuatorType.WATER_PUMP;
            water = profile.mitigationResourceCosts().lowSoilMoistureWater();
        } else if (reading.sensorType() == SensorType.LIGHT) {
            message = reading.chamberId().value() + " Low Light Level";
            actuator = ActuatorType.GROW_LIGHT;
        } else {
            message = reading.chamberId().value() + " Low Nutrient Level";
            actuator = ActuatorType.NUTRIENT_INJECTOR;
        }

        Instant timestamp = reading.timestamp() == null ? clock.instant() : reading.timestamp();
        AlertEvent alert = new AlertEvent(reading.chamberId(), profile.name(), AlertSeverity.WARNING, message, timestamp);
        return new EvaluationResult(Optional.of(alert), Optional.ofNullable(actuator), water, energy);
    }

    public Optional<CropProfile> profileFor(String cropName) {
        return registry.get(cropName);
    }

    private static ThresholdRange rangeFor(CropProfile profile, SensorType type) {
        return switch (type) {
            case TEMPERATURE -> profile.temperature();
            case HUMIDITY -> profile.humidity();
            case SOIL_MOISTURE -> profile.soilMoisture();
            case LIGHT -> profile.light();
            case NUTRIENT -> profile.nutrient();
        };
    }
}
