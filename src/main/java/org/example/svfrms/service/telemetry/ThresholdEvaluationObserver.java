package org.example.svfrms.service.telemetry;

import org.example.svfrms.command.*;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.CropThresholdStrategy.EvaluationResult;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Telemetry observer responsible for evaluating incoming sensor telemetry against
 * crop profile thresholds and dispatching necessary mitigation commands and alert events.
 */
public class ThresholdEvaluationObserver implements TelemetryObserver {

    private final CropRegistry cropRegistry;
    private final CropThresholdStrategy strategy;
    private final BoundedControlQueue queue;
    private final ResourceAccountingService metrics;
    private final Map<ChamberId, ChamberSummaryTracker> summaryMap;
    private final Consumer<AlertEvent> alertConsumer;

    public ThresholdEvaluationObserver(CropRegistry cropRegistry,
                                       CropThresholdStrategy strategy,
                                       BoundedControlQueue queue,
                                       ResourceAccountingService metrics,
                                       Map<ChamberId, ChamberSummaryTracker> summaryMap) {
        this(cropRegistry, strategy, queue, metrics, summaryMap, null);
    }

    public ThresholdEvaluationObserver(CropRegistry cropRegistry,
                                       CropThresholdStrategy strategy,
                                       BoundedControlQueue queue,
                                       ResourceAccountingService metrics,
                                       Map<ChamberId, ChamberSummaryTracker> summaryMap,
                                       Consumer<AlertEvent> alertConsumer) {
        this.cropRegistry = Objects.requireNonNull(cropRegistry, "cropRegistry must not be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        this.queue = queue;
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.summaryMap = Objects.requireNonNull(summaryMap, "summaryMap must not be null");
        this.alertConsumer = alertConsumer;
    }

    @Override
    public void onTelemetry(SensorReading reading) {
        Optional<CropProfile> profileOpt = cropRegistry.get(reading.crop());
        if (profileOpt.isEmpty()) {
            return;
        }

        CropProfile profile = profileOpt.get();
        EvaluationResult result = strategy.evaluate(reading, profile);

        if (result.alert().isPresent()) {
            AlertEvent alert = result.alert().get();
            String alertMsg = alert.message();

            // Terminal output matching legacy format
            System.out.println("[" + reading.chamberId().value() + "][" + reading.crop() + "][ALERT] " + alertMsg);

            // Record metrics atomically
            metrics.recordAlertGenerated();
            if (result.energyUsageCost() > 0) {
                metrics.recordEnergyUsage(result.energyUsageCost());
            }
            if (result.waterUsageCost() > 0) {
                metrics.recordWaterUsage(result.waterUsageCost());
            }

            // Record alert in chamber summary
            ChamberSummaryTracker summary = summaryMap.get(reading.chamberId());
            if (summary != null) {
                summary.recordAlert(alertMsg);
            }

            // Forward to alert consumer (e.g. AlertManagementService)
            if (alertConsumer != null) {
                alertConsumer.accept(alert);
            }
        }

        if (result.mitigationActuator().isPresent() && queue != null) {
            DeviceCommand command = createCommand(result.mitigationActuator().get(), reading.chamberId());
            if (command != null) {
                queue.offer(command);
            }
        }
    }

    private DeviceCommand createCommand(ActuatorType type, ChamberId chamberId) {
        return switch (type) {
            case COOLING_FAN -> new CoolingFanCommand(chamberId);
            case WATER_PUMP -> new WaterPumpCommand(chamberId);
            case NUTRIENT_INJECTOR -> new NutrientInjectorCommand(chamberId);
            case HUMIDIFIER -> new HumidifierCommand(chamberId);
            case DEHUMIDIFIER -> new DehumidifierCommand(chamberId);
            case GROW_LIGHT -> new GrowLightCommand(chamberId);
        };
    }
}
