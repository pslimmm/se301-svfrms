package org.example.svfrms;

import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.example.svfrms.service.telemetry.ThresholdEvaluationObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 10: Section 4.5 Feature Extension & Dynamic Crop Management Tests")
public class FeatureExtensionCropTest {

    private CropRegistry registry;
    private CropThresholdStrategy strategy;
    private BoundedControlQueue queue;
    private ResourceAccountingService metrics;
    private AlertManagementService alertService;
    private Map<ChamberId, ChamberSummaryTracker> summaryMap;
    private TelemetryPublisher publisher;

    @BeforeEach
    void setUp() {
        registry = new CropRegistry();
        registry.registerDefaults();
        strategy = new DefaultCropThresholdStrategy();
        queue = new BoundedControlQueue(100);
        metrics = new ResourceAccountingService();
        alertService = new AlertManagementService();
        summaryMap = new ConcurrentHashMap<>();

        publisher = new TelemetryPublisher();
        publisher.registerObserver(new ThresholdEvaluationObserver(
                registry, strategy, queue, metrics, summaryMap, alertService::publishAlert
        ));
    }

    @Test
    @DisplayName("Open-Closed Principle: Dynamically register Kale, Bell Pepper, Bok Choy, and Eggplant without modifying core engine")
    void testDynamicCropAddition() throws InterruptedException {
        // Register Kale
        registry.register(CropProfile.builder("Kale")
                .temperature(15.0, 22.0)
                .humidity(55.0, 75.0)
                .soilMoisture(50.0, 70.0)
                .light(400.0, 900.0)
                .nutrient(45.0, 90.0)
                .build());

        // Register Bell Pepper
        registry.register(CropProfile.builder("Bell Pepper")
                .temperature(20.0, 26.0)
                .humidity(60.0, 75.0)
                .soilMoisture(55.0, 70.0)
                .light(500.0, 1000.0)
                .nutrient(40.0, 85.0)
                .build());

        // Register Bok Choy
        registry.register(CropProfile.builder("Bok Choy")
                .temperature(13.0, 24.0)
                .humidity(60.0, 80.0)
                .soilMoisture(60.0, 80.0)
                .light(400.0, 800.0)
                .nutrient(35.0, 75.0)
                .build());

        // Register Eggplant
        registry.register(CropProfile.builder("Eggplant")
                .temperature(21.0, 29.0)
                .humidity(60.0, 85.0)
                .soilMoisture(50.0, 70.0)
                .light(600.0, 1200.0)
                .nutrient(50.0, 90.0)
                .build());

        ChamberId ch5 = ChamberId.of("CH5");
        summaryMap.put(ch5, new ChamberSummaryTracker(ch5));

        // Send out-of-range temperature for Kale (28C > 22C max)
        publisher.publish(new SensorReading(ch5, "Kale", SensorType.TEMPERATURE, 28.0, Instant.now()));

        assertThat(queue.size()).isEqualTo(1);
        DeviceCommand cmd = queue.take();
        assertThat(cmd.getActuatorType()).isEqualTo(ActuatorType.COOLING_FAN);
        assertThat(alertService.getActiveAlertsCount()).isEqualTo(1);
        assertThat(alertService.getActiveAlertsSnapshot().get(0).message()).contains("Kale High Temp");
    }

    @Test
    @DisplayName("Open-Closed Principle: Dynamically unregister a crop profile cleanly removes evaluation")
    void testDynamicCropRemoval() {
        assertThat(registry.get("Lettuce")).isPresent();
        boolean removed = registry.unregister("Lettuce");
        assertThat(removed).isTrue();
        assertThat(registry.get("Lettuce")).isEmpty();

        ChamberId ch3 = ChamberId.of("CH3");
        summaryMap.put(ch3, new ChamberSummaryTracker(ch3));

        // Reading for unregistered crop is safely ignored without throwing exceptions
        publisher.publish(new SensorReading(ch3, "Lettuce", SensorType.TEMPERATURE, 35.0, Instant.now()));
        assertThat(queue.size()).isEqualTo(0);
        assertThat(alertService.getActiveAlertsCount()).isEqualTo(0);
    }
}
