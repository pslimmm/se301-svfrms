package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.crop.*;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.service.crop.*;

import java.util.Objects;
import java.util.Optional;

/** Resolves the crop profile and applies the threshold strategy. */
public final class ThresholdEvaluator {
    private final CropRegistry crops;
    private final CropThresholdStrategy strategy;

    public ThresholdEvaluator(CropRegistry crops, CropThresholdStrategy strategy) {
        this.crops = Objects.requireNonNull(crops, "crops");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public EvaluationResult evaluate(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        Optional<CropProfile> profile = crops.get(reading.crop());
        return profile.map(value -> strategy.evaluate(reading, value))
                .orElseGet(() -> strategy.evaluateWithoutProfile(reading));
    }
}
