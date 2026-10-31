package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.crop.*;
import org.example.svfrms.domain.sensor.*;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.*;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Adapts telemetry notifications to the processing workflow. */
public final class ThresholdEvaluationObserver implements TelemetryObserver {
    private final TelemetryProcessingService processing;

    public ThresholdEvaluationObserver(TelemetryProcessingService processing) {
        this.processing = Objects.requireNonNull(processing, "processing");
    }

    /** Compatibility constructor; new wiring should supply the processing service directly. */
    public ThresholdEvaluationObserver(CropRegistry crops, CropThresholdStrategy strategy,
                                       AlertManagementService alerts, ActuatorExecutionService actuators,
                                       ResourceAccountingService metrics,
                                       Map<ChamberId, ChamberStateTracker> chamberStates,
                                       Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                       Duration enqueueTimeout) {
        this(crops, strategy, alerts, actuators, metrics, chamberStates, chamberSummaries,
                enqueueTimeout, System.out::println);
    }

    /** Compatibility constructor; new wiring should supply the processing service directly. */
    public ThresholdEvaluationObserver(CropRegistry crops, CropThresholdStrategy strategy,
                                       AlertManagementService alerts, ActuatorExecutionService actuators,
                                       ResourceAccountingService metrics,
                                       Map<ChamberId, ChamberStateTracker> chamberStates,
                                       Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                       Duration enqueueTimeout, Consumer<String> alertOutput) {
        this(new TelemetryProcessingService(
                new ChamberStateUpdater(chamberStates),
                new ThresholdEvaluator(crops, strategy),
                new ThresholdAlertRecorder(alerts, metrics, chamberSummaries, alertOutput),
                new MitigationDispatcher(actuators, enqueueTimeout)));
    }

    @Override
    public void onTelemetry(SensorReading reading) {
        processing.process(reading);
    }
}
