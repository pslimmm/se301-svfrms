package org.example.svfrms.service.telemetry;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryPublisherTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    void publishesToLoggerAndThresholdObserverWithoutCoupling() throws Exception {
        ChamberId chamber = new ChamberId("CH1");
        ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24, 70, 60, 600, 70));
        ChamberSummaryTracker summary = new ChamberSummaryTracker();
        ResourceAccountingService metrics = new ResourceAccountingService();
        CropRegistry crops = CropRegistry.withStandardProfiles();
        AlertManagementService alerts = new AlertManagementService();
        ActuatorExecutionService actuators = new ActuatorExecutionService(
                new BoundedControlQueue(), Map.of(chamber, state), Map.of(chamber, summary), metrics, ignored -> {});
        actuators.start();
        List<String> log = new CopyOnWriteArrayList<>();
        TelemetryPublisher publisher = new TelemetryPublisher();
        publisher.subscribe(new ConsoleLoggingObserver(log::add, Clock.fixed(NOW, ZoneOffset.UTC)));
        publisher.subscribe(new ThresholdEvaluationObserver(crops,
                new DefaultCropThresholdStrategy(crops, Clock.fixed(NOW, ZoneOffset.UTC)),
                alerts, actuators, metrics, Map.of(chamber, state), Map.of(chamber, summary), Duration.ofSeconds(1), ignored -> {}));

        try {
            publisher.publish(new SensorReading(chamber, "Tomato", SensorType.TEMPERATURE, 29, NOW));
        } finally {
            actuators.close();
        }

        assertThat(log).singleElement().asString().endsWith(" : Processing Tomato Temperature = 29.0");
        assertThat(alerts.getActiveAlertsSnapshot()).singleElement().satisfies(alert ->
                assertThat(alert.message()).isEqualTo("CH1 Tomato High Temp"));
        assertThat(summary.snapshot().alerts()).isEqualTo(1);
        assertThat(summary.snapshot().mitigationActions()).isEqualTo(1);
        assertThat(state.snapshot().temperature()).isEqualTo(24);
        assertThat(metrics.generatedAlerts()).isEqualTo(1);
        assertThat(metrics.energyUsage()).isEqualTo(10);
    }

    @Test
    void subscribersCanBeAddedAndRemovedConcurrentlyWithPublishing() {
        TelemetryPublisher publisher = new TelemetryPublisher();
        AtomicInteger calls = new AtomicInteger();
        TelemetryObserver observer = ignored -> calls.incrementAndGet();
        publisher.subscribe(observer);
        publisher.publish(reading());
        publisher.unsubscribe(observer);
        publisher.publish(reading());

        assertThat(calls).hasValue(1);
    }

    private static SensorReading reading() {
        return new SensorReading(new ChamberId("CH1"), "Tomato", SensorType.TEMPERATURE, 24, NOW);
    }
}
