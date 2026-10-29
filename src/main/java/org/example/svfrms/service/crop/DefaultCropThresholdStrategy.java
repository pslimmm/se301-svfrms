package org.example.svfrms.service.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;

import java.time.Instant;

/**
 * Standard threshold evaluation implementation matching legacy operational semantics and string formats.
 */
public class DefaultCropThresholdStrategy implements CropThresholdStrategy {

    @Override
    public EvaluationResult evaluate(SensorReading reading, CropProfile profile) {
        String chamber = reading.chamberId().value();
        String crop = reading.crop();
        double value = reading.value();
        Instant now = reading.timestamp();

        if (reading.sensorType() == SensorType.TEMPERATURE) {
            if (profile.temperature().isAbove(value)) {
                String msg = chamber + " " + crop + " High Temp";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                long energyCost = ("Tomato".equals(crop) || "Strawberry".equals(crop)) ? 10 : 0;
                return EvaluationResult.alertAndActuate(alert, ActuatorType.COOLING_FAN, energyCost, 0);
            }
            if (profile.temperature().isBelow(value)) {
                String msg = chamber + " " + crop + " Low Temp";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                long energyCost = ("Tomato".equals(crop) || "Strawberry".equals(crop)) ? 15 : 0;
                return EvaluationResult.alertOnly(alert, energyCost, 0);
            }
        } else if (reading.sensorType() == SensorType.HUMIDITY) {
            if (profile.humidity().isAbove(value)) {
                String msg = chamber + " " + crop + " High Humidity";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                return EvaluationResult.alertAndActuate(alert, ActuatorType.DEHUMIDIFIER, 0, 0);
            }
            if (profile.humidity().isBelow(value)) {
                String msg = chamber + " " + crop + " Low Humidity";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                long waterCost = "Tomato".equals(crop) ? 20 : ("Strawberry".equals(crop) ? 25 : 0);
                return EvaluationResult.alertAndActuate(alert, ActuatorType.HUMIDIFIER, 0, waterCost);
            }
        } else if (reading.sensorType() == SensorType.SOIL_MOISTURE) {
            if (profile.soilMoisture().isBelow(value)) {
                String msg = chamber + " " + crop + " Low Soil Moisture";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                return EvaluationResult.alertAndActuate(alert, ActuatorType.WATER_PUMP, 0, 50);
            }
        } else if (reading.sensorType() == SensorType.LIGHT) {
            if (value < 500.0) {
                String msg = chamber + " Low Light Level";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                return EvaluationResult.alertAndActuate(alert, ActuatorType.GROW_LIGHT, 0, 0);
            }
        } else if (reading.sensorType() == SensorType.NUTRIENT) {
            if (value < 40.0) {
                String msg = chamber + " Low Nutrient Level";
                AlertEvent alert = new AlertEvent(reading.chamberId(), crop, AlertSeverity.WARNING, msg, now);
                return EvaluationResult.alertAndActuate(alert, ActuatorType.NUTRIENT_INJECTOR, 0, 0);
            }
        }

        return EvaluationResult.normal();
    }
}
