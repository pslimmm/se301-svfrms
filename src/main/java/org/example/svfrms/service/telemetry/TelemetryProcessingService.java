package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.service.crop.EvaluationResult;

import java.util.Objects;

/** Coordinates telemetry processing; each collaborator owns one processing step. */
public final class TelemetryProcessingService {
    private final ChamberStateUpdater stateUpdater;
    private final ThresholdEvaluator evaluator;
    private final ThresholdAlertRecorder alertRecorder;
    private final MitigationDispatcher mitigationDispatcher;

    public TelemetryProcessingService(ChamberStateUpdater stateUpdater, ThresholdEvaluator evaluator,
                                      ThresholdAlertRecorder alertRecorder, MitigationDispatcher mitigationDispatcher) {
        this.stateUpdater = Objects.requireNonNull(stateUpdater, "stateUpdater");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.alertRecorder = Objects.requireNonNull(alertRecorder, "alertRecorder");
        this.mitigationDispatcher = Objects.requireNonNull(mitigationDispatcher, "mitigationDispatcher");
    }

    public void process(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        stateUpdater.update(reading);
        EvaluationResult result = evaluator.evaluate(reading);
        alertRecorder.record(reading, result);
        mitigationDispatcher.dispatch(reading, result);
    }
}
