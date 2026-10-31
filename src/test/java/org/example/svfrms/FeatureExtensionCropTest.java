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
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.EvaluationResult;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.example.svfrms.service.telemetry.ThresholdEvaluationObserver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        assertThat(alerts.generatedAlertCount()).isEqualTo(4);
        assertThat(metrics.energyUsage()).isEqualTo(40);
    }

    @Test
    void replacementAndRemovalUseOneProfilePerReading() throws Exception {
        for (boolean remove : List.of(false, true)) {
            CropRegistry crops = CropRegistry.withStandardProfiles();
            crops.register(profile("Clone"));
            var delegate = new DefaultCropThresholdStrategy(crops);
            CountDownLatch lookedUp = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean first = new AtomicBoolean(true);
            CropThresholdStrategy paused = new CropThresholdStrategy() {
                public EvaluationResult evaluate(SensorReading reading, CropProfile profile) {
                    if (first.compareAndSet(true, false)) {
                        lookedUp.countDown();
                        try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                        catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                    }
                    return delegate.evaluate(reading, profile);
                }
                public EvaluationResult evaluateWithoutProfile(SensorReading reading) {
                    return delegate.evaluateWithoutProfile(reading);
                }
            };
            ChamberId chamber = new ChamberId("CH1");
            ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24,70,60,600,70));
            ChamberSummaryTracker summary = new ChamberSummaryTracker();
            ResourceAccountingService resources = new ResourceAccountingService();
            AlertManagementService alerts = new AlertManagementService();
            try (var actuators = new ActuatorExecutionService(new BoundedControlQueue(), Map.of(chamber,state),
                    Map.of(chamber,summary), resources, ignored -> {});
                 var executor = Executors.newSingleThreadExecutor()) {
                actuators.start();
                var observer = new ThresholdEvaluationObserver(crops, paused, alerts, actuators, resources,
                        Map.of(chamber,state), Map.of(chamber,summary), Duration.ofSeconds(1), ignored -> {});
                var accepted = executor.submit(() -> observer.onTelemetry(new SensorReading(chamber,"Clone",SensorType.HUMIDITY,59,NOW)));
                try {
                    assertThat(lookedUp.await(2,TimeUnit.SECONDS)).isTrue();
                    if (remove) assertThat(crops.unregister("Clone")).isTrue();
                    else {
                        CropProfile strawberry = crops.get("Strawberry").orElseThrow();
                        crops.register(copyAs("Clone", strawberry));
                    }
                } finally { release.countDown(); }
                accepted.get(2,TimeUnit.SECONDS);
                assertThat(resources.waterUsage()).isEqualTo(20);
                observer.onTelemetry(new SensorReading(chamber,"Clone",SensorType.HUMIDITY,65,NOW));
                if (remove) observer.onTelemetry(new SensorReading(chamber,"Clone",SensorType.LIGHT,499,NOW));
                actuators.close();
                assertThat(resources.waterUsage()).isEqualTo(remove ? 20 : 45);
                assertThat(alerts.generatedAlertCount()).isEqualTo(2);
                assertThat(alerts.getActiveAlertsSnapshot()).hasSize(2);
                assertThat(alerts.getActiveAlertsSnapshot().getFirst().message()).isEqualTo("CH1 Clone Low Humidity");
                assertThat(summary.snapshot().mitigationActions()).isEqualTo(2);
            }
        }
    }

    private static CropProfile profile(String name) {
        return copyAs(name, CropRegistry.withStandardProfiles().get("Tomato").orElseThrow());
    }

    private static CropProfile copyAs(String name, CropProfile source) {
        return CropProfile.builder(name)
                .temperature(source.temperature()).humidity(source.humidity()).soilMoisture(source.soilMoisture())
                .light(source.light()).nutrient(source.nutrient())
                .mitigationResourceCosts(source.mitigationResourceCosts())
                .enabledSensors(source.enabledSensors())
                .build();
    }
}
