package org.example.svfrms.domain.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Task 3: Open-Closed Crop Strategy & Dynamic Registry Tests")
public class CropRegistryTest {

    private CropRegistry registry;
    private CropThresholdStrategy strategy;

    @BeforeEach
    void setUp() {
        registry = new CropRegistry();
        registry.registerDefaults();
        strategy = new DefaultCropThresholdStrategy();
    }

    @Test
    @DisplayName("ThresholdRange validates invariants")
    void testThresholdRangeInvariants() {
        ThresholdRange range = new ThresholdRange(20.0, 30.0);
        assertThat(range.min()).isEqualTo(20.0);
        assertThat(range.max()).isEqualTo(30.0);
        assertThat(range.isBelow(19.9)).isTrue();
        assertThat(range.isAbove(30.1)).isTrue();
        assertThat(range.isWithin(25.0)).isTrue();

        assertThatThrownBy(() -> new ThresholdRange(35.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Default crops are pre-registered with correct operational boundaries")
    void testDefaultCropsRegistered() {
        assertThat(registry.get("Tomato")).isPresent();
        assertThat(registry.get("Strawberry")).isPresent();
        assertThat(registry.get("Lettuce")).isPresent();
        assertThat(registry.get("Spinach")).isPresent();
        assertThat(registry.get("Chili")).isPresent();
        assertThat(registry.get("Cucumber")).isPresent();

        CropProfile tomato = registry.get("Tomato").get();
        assertThat(tomato.temperature().min()).isEqualTo(22.0);
        assertThat(tomato.temperature().max()).isEqualTo(28.0);
        assertThat(tomato.humidity().min()).isEqualTo(60.0);
        assertThat(tomato.humidity().max()).isEqualTo(80.0);
        assertThat(tomato.soilMoisture().min()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("Evaluate Tomato temperature triggers high temp alert and cooling fan")
    void testTomatoHighTempEvaluation() {
        CropProfile tomato = registry.get("Tomato").get();
        SensorReading reading = new SensorReading(
                ChamberId.of("CH1"), "Tomato", SensorType.TEMPERATURE, 31.0, Instant.now());

        CropThresholdStrategy.EvaluationResult result = strategy.evaluate(reading, tomato);

        assertThat(result.alert()).isPresent();
        assertThat(result.alert().get().message()).contains("Tomato High Temp");
        assertThat(result.alert().get().severity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(result.mitigationActuator()).isPresent();
        assertThat(result.mitigationActuator().get()).isEqualTo(ActuatorType.COOLING_FAN);
    }

    @Test
    @DisplayName("Open-Closed Principle: Dynamically register Kale without code modification")
    void testDynamicRegistrationOfNewCrop() {
        CropProfile kale = CropProfile.builder("Kale")
                .temperature(12.0, 20.0)
                .humidity(60.0, 75.0)
                .soilMoisture(50.0, 70.0)
                .light(400.0, 800.0)
                .nutrient(50.0, 75.0)
                .build();

        registry.register(kale);

        assertThat(registry.get("Kale")).isPresent();

        // High temperature test for Kale (exceeds 20C)
        SensorReading reading = new SensorReading(
                ChamberId.of("CH5"), "Kale", SensorType.TEMPERATURE, 25.0, Instant.now());
        CropThresholdStrategy.EvaluationResult result = strategy.evaluate(reading, kale);

        assertThat(result.alert()).isPresent();
        assertThat(result.alert().get().message()).contains("Kale High Temp");
        assertThat(result.mitigationActuator()).contains(ActuatorType.COOLING_FAN);
    }

    @Test
    @DisplayName("Dynamic unregistration removes crop profile")
    void testUnregistration() {
        assertThat(registry.get("Cucumber")).isPresent();
        boolean removed = registry.unregister("Cucumber");
        assertThat(removed).isTrue();
        assertThat(registry.get("Cucumber")).isEmpty();
    }
}
