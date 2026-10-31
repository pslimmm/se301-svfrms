package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.EvaluationResult;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Records a detected condition and its accounting, summary and output effects. */
public final class ThresholdAlertRecorder {
    private final AlertManagementService alerts;
    private final ResourceAccountingService metrics;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final Consumer<String> alertOutput;

    public ThresholdAlertRecorder(AlertManagementService alerts, ResourceAccountingService metrics,
                                  Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                  Consumer<String> alertOutput) {
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.chamberSummaries = Objects.requireNonNull(chamberSummaries, "chamberSummaries");
        this.alertOutput = Objects.requireNonNull(alertOutput, "alertOutput");
    }

    public void record(SensorReading reading, EvaluationResult result) {
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(result, "result");
        result.alert().ifPresent(alert -> {
            alerts.publishAlert(alert);
            // The charge belongs to the detected condition, even if queuing fails later.
            metrics.recordWater(result.waterUsage());
            metrics.recordEnergy(result.energyUsage());
            ChamberSummaryTracker summary = chamberSummaries.get(reading.chamberId());
            if (summary == null) summary = chamberSummaries.computeIfAbsent(reading.chamberId(), ignored -> new ChamberSummaryTracker());
            if (summary != null) {
                summary.recordAlert(alert.message());
            }
            alertOutput.accept("[" + alert.chamberId().value() + "][" + alert.crop() + "][ALERT] " + alert.message());
        });
    }
}
