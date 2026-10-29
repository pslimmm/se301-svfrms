package org.example.svfrms;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.crop.MitigationResourceCosts;
import org.example.svfrms.domain.crop.ThresholdRange;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.example.svfrms.service.telemetry.ThresholdEvaluationObserver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureExtensionCropTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    void newCropsUseTheSameEvaluatorAndCanBeRemovedAtRuntime() throws Exception {
        CropRegistry crops = new CropRegistry();
        List<String> cropNames = List.of("Kale", "Bell Pepper", "Bok Choy", "Eggplant");
        cropNames.stream().map(FeatureExtensionCropTest::profile).forEach(crops::register);

        ChamberId chamber = new ChamberId("CH1");
        ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24, 70, 60, 600, 70));
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        ResourceAccountingService metrics = new ResourceAccountingService();
        AlertManagementService alerts = new AlertManagementService();
        ActuatorExecutionService actuators = new ActuatorExecutionService(
                new BoundedControlQueue(), Map.of(chamber, state), Map.of(chamber, summary), metrics, ignored -> {});
        actuators.start();
        TelemetryPublisher telemetry = new TelemetryPublisher(ignored -> {});
        telemetry.subscribe(new ThresholdEvaluationObserver(crops,
                new DefaultCropThresholdStrategy(crops), alerts, actuators, metrics,
                Map.of(chamber, state), Map.of(chamber, summary), Duration.ofSeconds(1), ignored -> {}));

        try {
            for (String crop : cropNames) {
                telemetry.publish(new SensorReading(chamber, crop, SensorType.TEMPERATURE, 30, NOW));
            }
            assertThat(crops.unregister("Eggplant")).isTrue();
            telemetry.publish(new SensorReading(chamber, "Eggplant", SensorType.TEMPERATURE, 30, NOW));
        } finally {
            actuators.close();
        }

        assertThat(crops.get("Eggplant")).isEmpty();
        assertThat(alerts.getActiveAlertsSnapshot()).hasSize(4)
                .extracting(alert -> alert.crop()).containsExactlyElementsOf(cropNames);
        assertThat(summary.snapshot().alerts()).isEqualTo(4);
        assertThat(summary.snapshot().mitigationActions()).isEqualTo(4);
        assertThat(metrics.generatedAlerts()).isEqualTo(4);
        assertThat(metrics.energyUsage()).isEqualTo(40);
    }

    private static CropProfile profile(String name) {
        return CropProfile.builder(name)
                .temperature(new ThresholdRange(15, 25))
                .humidity(new ThresholdRange(40, 90))
                .soilMoisture(new ThresholdRange(30, 80))
                .light(new ThresholdRange(500, 1_500))
                .nutrient(new ThresholdRange(40, 100))
                .mitigationResourceCosts(new MitigationResourceCosts(10, 0, 0, 0))
                .build();
    }
}
