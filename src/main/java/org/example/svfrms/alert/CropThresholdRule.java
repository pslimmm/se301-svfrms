package org.example.svfrms.alert;

import java.util.Optional;

import org.example.svfrms.crop.CropRegistry;
import org.example.svfrms.crop.CropThreshold;
import org.example.svfrms.model.Alert;
import org.example.svfrms.model.AlertSeverity;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.SensorReading;

/**
 * Checks a reading against the crop's own range, e.g. "CH1 Tomato High Temp".
 *
 * <p>This single class replaces about 40 lines of copy-pasted per-crop blocks in the legacy code.
 * It works for any crop in the registry, including crops added later (Kale, Eggplant, ...).
 */
public final class CropThresholdRule implements AlertRule {

    private final CropRegistry crops;

    public CropThresholdRule(CropRegistry crops) {
        this.crops = crops;
    }

    @Override
    public Optional<Alert> evaluate(SensorReading reading) {
        if (!reading.isValid()) {
            return Optional.empty();
        }
        return crops.find(reading.crop())
                .flatMap(profile -> profile.threshold(reading.type()))
                .flatMap(threshold -> check(reading, threshold));
    }

    private Optional<Alert> check(SensorReading reading, CropThreshold threshold) {
        if (threshold.range().isAbove(reading.value())) {
            return Optional.of(alert(reading, Breach.HIGH));
        }
        if (threshold.range().isBelow(reading.value())) {
            return Optional.of(alert(reading, Breach.LOW));
        }
        return Optional.empty();
    }

    private static Alert alert(SensorReading reading, Breach breach) {
        String message = reading.chamberId() + " " + reading.crop() + " " + breach.word() + " " + reading.type().alertName();
        return new Alert(reading.chamberId(), reading.crop(), reading.type(), breach, AlertSeverity.WARNING, message);
    }
}
