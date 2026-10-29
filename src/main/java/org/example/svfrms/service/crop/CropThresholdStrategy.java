package org.example.svfrms.service.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.sensor.SensorReading;

import java.util.Optional;

/**
 * Strategy interface defining the evaluation of sensor readings against crop threshold profiles.
 */
public interface CropThresholdStrategy {

    record EvaluationResult(
            Optional<AlertEvent> alert,
            Optional<ActuatorType> mitigationActuator,
            long energyUsageCost,
            long waterUsageCost
    ) {
        public static EvaluationResult normal() {
            return new EvaluationResult(Optional.empty(), Optional.empty(), 0, 0);
        }

        public static EvaluationResult alertOnly(AlertEvent alert, long energyCost, long waterCost) {
            return new EvaluationResult(Optional.of(alert), Optional.empty(), energyCost, waterCost);
        }

        public static EvaluationResult alertAndActuate(
                AlertEvent alert, ActuatorType actuator, long energyCost, long waterCost) {
            return new EvaluationResult(Optional.of(alert), Optional.of(actuator), energyCost, waterCost);
        }
    }

    EvaluationResult evaluate(SensorReading reading, CropProfile profile);
}
