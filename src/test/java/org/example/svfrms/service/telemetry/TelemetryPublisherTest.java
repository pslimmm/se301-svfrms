package org.example.svfrms.service.telemetry;

import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 6: Telemetry Observer Pattern & Event Pipeline Tests")
public class TelemetryPublisherTest {

    private TelemetryPublisher publisher;
    private CropRegistry cropRegistry;
    private CropThresholdStrategy strategy;
    private BoundedControlQueue queue;
    private ResourceAccountingService metrics;
    private Map<ChamberId, ChamberSummaryTracker> summaryMap;

    @BeforeEach
    void setUp() {
        publisher = new TelemetryPublisher();
        cropRegistry = new CropRegistry();
        cropRegistry.registerDefaults();
        strategy = new DefaultCropThresholdStrategy();
        queue = new BoundedControlQueue(100);
        metrics = new ResourceAccountingService();
        summaryMap = new ConcurrentHashMap<>();
        summaryMap.put(ChamberId.of("CH1"), new ChamberSummaryTracker(ChamberId.of("CH1")));
    }

    @Test
    @DisplayName("TelemetryPublisher broadcasts readings to all registered observers")
    void testPublisherBroadcastsToMultipleObservers() {
        List<SensorReading> received = new ArrayList<>();
        publisher.registerObserver(received::add);

        SensorReading reading = new SensorReading(
                ChamberId.of("CH1"), "Tomato", SensorType.TEMPERATURE, 25.0, Instant.now());
        publisher.publish(reading);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(reading);
    }

    @Test
    @DisplayName("ThresholdEvaluationObserver triggers actuation command and metrics on out-of-range sensor reading")
    void testThresholdObserverTriggersMitigation() throws InterruptedException {
        ThresholdEvaluationObserver evalObserver = new ThresholdEvaluationObserver(
                cropRegistry, strategy, queue, metrics, summaryMap);
        publisher.registerObserver(evalObserver);

        // High temperature reading (32C > 28C max for Tomato)
        SensorReading reading = new SensorReading(
                ChamberId.of("CH1"), "Tomato", SensorType.TEMPERATURE, 32.0, Instant.now());
        publisher.publish(reading);

        assertThat(queue.size()).isEqualTo(1);
        DeviceCommand command = queue.take();
        assertThat(command.getActuatorType()).isEqualTo(ActuatorType.COOLING_FAN);
        assertThat(metrics.getTotalAlertsGenerated()).isEqualTo(1);
        assertThat(metrics.getEnergyUsageKWh()).isEqualTo(10); // Tomato high temp adds 10 kWh
    }

    @Test
    @DisplayName("ConsoleLoggingObserver records sensor telemetry into memory log")
    void testConsoleLoggingObserver() {
        ConsoleLoggingObserver logger = new ConsoleLoggingObserver();
        publisher.registerObserver(logger);

        SensorReading reading = new SensorReading(
                ChamberId.of("CH1"), "Tomato", SensorType.TEMPERATURE, 25.0, Instant.now());
        publisher.publish(reading);

        assertThat(logger.getSensorLogs()).hasSize(1);
        assertThat(logger.getSensorLogs().get(0)).contains("Processing Tomato Temperature = 25.0");
    }
}
