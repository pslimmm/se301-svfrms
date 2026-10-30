package org.example.svfrms.alert;

import java.util.Optional;

import org.example.svfrms.model.Alert;
import org.example.svfrms.model.AlertSeverity;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.SensorReading;

/**
 * Sensor Failure Alert from the brief (section 3.4): raised when a sensor reports an unusable value
 * (NaN or infinity). The legacy simulator never produces such values, so the default output is unchanged.
 * This rule shows how a new alert type is added: one new class plus one line in the composition root.
 */
public final class SensorFailureRule implements AlertRule {

    @Override
    public Optional<Alert> evaluate(SensorReading reading) {
        if (reading.isValid()) {
            return Optional.empty();
        }
        String message = reading.chamberId() + " " + reading.type().label() + " Sensor Failure";
        return Optional.of(new Alert(reading.chamberId(), reading.crop(), reading.type(),
                Breach.INVALID, AlertSeverity.CRITICAL, message));
    }
}
