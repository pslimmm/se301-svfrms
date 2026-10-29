package org.example.svfrms.service.crop;

import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.sensor.SensorReading;

@FunctionalInterface
public interface CropThresholdStrategy {
    EvaluationResult evaluate(SensorReading reading, CropProfile profile);
}
