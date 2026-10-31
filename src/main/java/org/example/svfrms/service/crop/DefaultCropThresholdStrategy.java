package org.example.svfrms.service.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.*;
import org.example.svfrms.domain.crop.*;
import org.example.svfrms.domain.sensor.*;

import java.util.*;

/** Evaluates crop ranges while keeping mitigation rules out of the registry. */
public final class DefaultCropThresholdStrategy implements CropThresholdStrategy {
    @Override
    public EvaluationResult evaluate(SensorReading reading, CropProfile profile) {
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(profile, "profile");
        if (!reading.crop().equals(profile.name())) {
            throw new IllegalArgumentException("Reading crop does not match the supplied profile");
        }

        if (!profile.enabledSensors().contains(reading.sensorType())) {
            return EvaluationResult.none();
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

        AlertEvent alert = new AlertEvent(reading.chamberId(), profile.name(), message, reading.timestamp());
        return new EvaluationResult(Optional.of(alert), Optional.ofNullable(actuator), water, energy);
    }

    @Override
    public EvaluationResult evaluateWithoutProfile(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        String message;
        ActuatorType actuator;
        if (reading.sensorType() == SensorType.LIGHT && reading.value() < CropProfile.DEFAULT_MIN_LIGHT_LUX) {
            message = reading.chamberId().value() + " Low Light Level";
            actuator = ActuatorType.GROW_LIGHT;
        } else if (reading.sensorType() == SensorType.NUTRIENT && reading.value() < CropProfile.DEFAULT_MIN_NUTRIENT_LEVEL_PERCENT) {
            message = reading.chamberId().value() + " Low Nutrient Level";
            actuator = ActuatorType.NUTRIENT_INJECTOR;
        } else {
            return EvaluationResult.none();
        }
        return new EvaluationResult(Optional.of(new AlertEvent(reading.chamberId(), reading.crop(),
                message, reading.timestamp())), Optional.of(actuator), 0, 0);
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
