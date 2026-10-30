package org.example.svfrms.alert;

import java.util.Optional;

import org.example.svfrms.model.Alert;
import org.example.svfrms.model.SensorReading;

/**
 * Strategy: one way of deciding whether a reading deserves an alert.
 *
 * <p>The legacy {@code processSensor()} hard-coded every check in one long if/else chain. Now each
 * kind of check is its own small class, and new checks can be added without editing existing ones.
 */
@FunctionalInterface
public interface AlertRule {

    Optional<Alert> evaluate(SensorReading reading);
}
