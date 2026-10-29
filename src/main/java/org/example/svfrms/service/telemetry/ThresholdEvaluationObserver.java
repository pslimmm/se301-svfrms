package org.example.svfrms.service.telemetry;

import org.example.svfrms.command.CoolingFanCommand;
import org.example.svfrms.command.DehumidifierCommand;
import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.command.GrowLightCommand;
import org.example.svfrms.command.HumidifierCommand;
import org.example.svfrms.command.NutrientInjectorCommand;
import org.example.svfrms.command.WaterPumpCommand;
import org.example.svfrms.concurrency.ActuatorRequest;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.EvaluationResult;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Evaluates each reading, records alerts and resource usage, and queues mitigations. */
public final class ThresholdEvaluationObserver implements TelemetryObserver {
    private final CropRegistry crops;
    private final CropThresholdStrategy strategy;
    private final AlertManagementService alerts;
    private final ActuatorExecutionService actuators;
    private final ResourceAccountingService metrics;
    private final Map<ChamberId, ChamberStateTracker> chamberStates;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final Duration enqueueTimeout;
    private final Consumer<String> alertOutput;

    public ThresholdEvaluationObserver(CropRegistry crops, CropThresholdStrategy strategy,
                                       AlertManagementService alerts, ActuatorExecutionService actuators,
                                       ResourceAccountingService metrics,
                                       Map<ChamberId, ChamberStateTracker> chamberStates,
                                       Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                       Duration enqueueTimeout) {
        this(crops, strategy, alerts, actuators, metrics, chamberStates, chamberSummaries,
                enqueueTimeout, System.out::println);
    }

    public ThresholdEvaluationObserver(CropRegistry crops, CropThresholdStrategy strategy,
                                       AlertManagementService alerts, ActuatorExecutionService actuators,
                                       ResourceAccountingService metrics,
                                       Map<ChamberId, ChamberStateTracker> chamberStates,
                                       Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                       Duration enqueueTimeout, Consumer<String> alertOutput) {
        this.crops = Objects.requireNonNull(crops, "crops");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.actuators = Objects.requireNonNull(actuators, "actuators");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.chamberStates = Map.copyOf(chamberStates);
        this.chamberSummaries = Map.copyOf(chamberSummaries);
        this.enqueueTimeout = Objects.requireNonNull(enqueueTimeout, "enqueueTimeout");
        this.alertOutput = Objects.requireNonNull(alertOutput, "alertOutput");
        if (enqueueTimeout.isNegative()) {
            throw new IllegalArgumentException("enqueueTimeout must not be negative");
        }
    }

    @Override
    public void onTelemetry(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        ChamberStateTracker tracker = chamberStates.get(reading.chamberId());
        if (tracker == null) {
            throw new IllegalArgumentException("No state tracker for chamber " + reading.chamberId().value());
        }
        updateState(tracker, reading);

        Optional<CropProfile> profile = crops.get(reading.crop());
        if (profile.isEmpty()) {
            return;
        }
        EvaluationResult result = strategy.evaluate(reading, profile.get());
        result.alert().ifPresent(alert -> {
            alerts.publishAlert(alert);
            metrics.recordAlert();
            ChamberSummaryTracker summary = chamberSummaries.get(reading.chamberId());
            if (summary != null) {
                summary.recordAlert(alert.message());
            }
            alertOutput.accept("[" + alert.chamberId().value() + "][" + alert.crop() + "][ALERT] " + alert.message());
        });

        Optional<ActuatorType> mitigation = result.mitigation();
        if (mitigation.isEmpty()) {
            metrics.recordWater(result.waterUsage());
            metrics.recordEnergy(result.energyUsage());
            return;
        }
        DeviceCommand command = commandFor(mitigation.get(), result);
        try {
            boolean accepted = actuators.submit(new ActuatorRequest(reading.chamberId(), command), enqueueTimeout);
            if (!accepted) {
                throw new IllegalStateException("Actuator queue remained full until timeout");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queuing actuator work", interrupted);
        }
    }

    private static void updateState(ChamberStateTracker tracker, SensorReading reading) {
        tracker.updateAndGet(state -> switch (reading.sensorType()) {
            case TEMPERATURE -> new ChamberSnapshot(reading.value(), state.humidity(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel());
            case HUMIDITY -> new ChamberSnapshot(state.temperature(), reading.value(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel());
            case SOIL_MOISTURE -> new ChamberSnapshot(state.temperature(), state.humidity(), reading.value(), state.lightLevel(), state.nutrientLevel());
            case LIGHT -> new ChamberSnapshot(state.temperature(), state.humidity(), state.soilMoisture(), reading.value(), state.nutrientLevel());
            case NUTRIENT -> new ChamberSnapshot(state.temperature(), state.humidity(), state.soilMoisture(), state.lightLevel(), reading.value());
        });
    }

    private static DeviceCommand commandFor(ActuatorType type, EvaluationResult result) {
        return switch (type) {
            case COOLING_FAN -> new CoolingFanCommand(result.energyUsage());
            case WATER_PUMP -> new WaterPumpCommand(result.waterUsage());
            case NUTRIENT_INJECTOR -> new NutrientInjectorCommand();
            case HUMIDIFIER -> new HumidifierCommand(result.waterUsage());
            case DEHUMIDIFIER -> new DehumidifierCommand();
            case GROW_LIGHT -> new GrowLightCommand();
        };
    }
}
