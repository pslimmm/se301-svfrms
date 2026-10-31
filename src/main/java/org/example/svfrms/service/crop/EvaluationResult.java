package org.example.svfrms.service.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;

import java.util.Objects;
import java.util.Optional;

/** Result of evaluating a reading, including the legacy resource deltas. */
public record EvaluationResult(Optional<AlertEvent> alert, Optional<ActuatorType> mitigation,
                               long waterUsage, long energyUsage) {
    public EvaluationResult {
        Objects.requireNonNull(alert, "alert");
        Objects.requireNonNull(mitigation, "mitigation");
        if (waterUsage < 0 || energyUsage < 0) {
            throw new IllegalArgumentException("Resource usage deltas must be non-negative");
        }
    }

    public static EvaluationResult none() {
        return new EvaluationResult(Optional.empty(), Optional.empty(), 0, 0);
    }
}
