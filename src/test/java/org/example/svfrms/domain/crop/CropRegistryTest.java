package org.example.svfrms.domain.crop;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CropRegistryTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    void validatesInclusiveThresholdRanges() {
        ThresholdRange range = new ThresholdRange(18, 24);

        assertThat(range.contains(18)).isTrue();
        assertThat(range.contains(24)).isTrue();
        assertThat(range.contains(24.01)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new ThresholdRange(25, 24));
    }

    @Test
    void standardRegistryContainsLegacyCropsAndSupportsDynamicRegistrationAndRemoval() {
        CropRegistry registry = CropRegistry.withStandardProfiles();
        assertThat(registry.get("tomato")).isPresent();
        assertThat(registry.get("Strawberry")).isPresent();
        assertThat(registry.get("Lettuce")).isPresent();
        assertThat(registry.get("Spinach")).isPresent();
        assertThat(registry.get("Chili")).isPresent();
        assertThat(registry.get("Cucumber")).isPresent();

        CropProfile kale = profile("Kale", new ThresholdRange(16, 22));
        registry.register(kale);
        assertThat(registry.get(" kale ")).contains(kale);
        assertThat(registry.unregister("KALE")).isTrue();
        assertThat(registry.get("Kale")).isEmpty();
        assertThat(registry.unregister("Kale")).isFalse();
    }

    @Test
    void thresholdStrategyUsesRegisteredProfileWithoutCropSpecificBranches() {
        CropRegistry registry = new CropRegistry();
        CropProfile kale = profile("Kale", new ThresholdRange(16, 22));
        registry.register(kale);
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(
                registry, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = strategy.evaluate(reading("Kale", SensorType.TEMPERATURE, 25), kale);

        assertThat(result.alert()).get().satisfies(alert -> {
            assertThat(alert.message()).isEqualTo("CH1 Kale High Temp");
            assertThat(alert.timestamp()).isEqualTo(NOW);
        });
        assertThat(result.mitigation()).contains(ActuatorType.COOLING_FAN);
        assertThat(result.energyUsage()).isEqualTo(10);
    }

    @Test
    void preservesLegacyTomatoSoilAndStrawberryHumidityMitigations() {
        CropRegistry registry = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(
                registry, Clock.fixed(NOW, ZoneOffset.UTC));

        var soil = strategy.evaluate(reading("Tomato", SensorType.SOIL_MOISTURE, 40), registry.get("Tomato").orElseThrow());
        var humidity = strategy.evaluate(reading("Strawberry", SensorType.HUMIDITY, 65), registry.get("Strawberry").orElseThrow());

        assertThat(soil.alert()).get().extracting(alert -> alert.message()).isEqualTo("CH1 Tomato Low Soil Moisture");
        assertThat(soil.mitigation()).contains(ActuatorType.WATER_PUMP);
        assertThat(soil.waterUsage()).isEqualTo(50);
        assertThat(humidity.alert()).get().extracting(alert -> alert.message()).isEqualTo("CH1 Strawberry Low Humidity");
        assertThat(humidity.mitigation()).contains(ActuatorType.HUMIDIFIER);
        assertThat(humidity.waterUsage()).isEqualTo(25);
    }

    @Test
    void temperatureResourceCostsMatchEveryLegacyCropProfile() {
        CropRegistry registry = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(registry, Clock.fixed(NOW, ZoneOffset.UTC));

        for (String crop : java.util.List.of("Tomato", "Strawberry", "Lettuce", "Spinach", "Chili", "Cucumber")) {
            CropProfile profile = registry.get(crop).orElseThrow();
            long expectedHigh = crop.equals("Tomato") || crop.equals("Strawberry") ? 10 : 0;
            long expectedLow = crop.equals("Tomato") || crop.equals("Strawberry") ? 15 : 0;

            assertThat(strategy.evaluate(reading(crop, SensorType.TEMPERATURE, 40), profile).energyUsage())
                    .as("high temperature energy for %s", crop).isEqualTo(expectedHigh);
            assertThat(strategy.evaluate(reading(crop, SensorType.TEMPERATURE, 0), profile).energyUsage())
                    .as("low temperature energy for %s", crop).isEqualTo(expectedLow);
        }
    }

    @Test
    void preservesChamberPrefixInLightAlerts() {
        CropRegistry registry = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(registry, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = strategy.evaluate(reading("Tomato", SensorType.LIGHT, 300), registry.get("Tomato").orElseThrow());

        assertThat(result.alert()).get().extracting(alert -> alert.message()).isEqualTo("CH1 Low Light Level");
    }

    @Test
    void preservesChamberPrefixInNutrientAlerts() {
        CropRegistry registry = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(registry, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = strategy.evaluate(reading("Tomato", SensorType.NUTRIENT, 20), registry.get("Tomato").orElseThrow());

        assertThat(result.alert()).get().extracting(alert -> alert.message()).isEqualTo("CH1 Low Nutrient Level");
    }

    @Test
    void onlyLegacyCropsChargeTemperatureEnergy() {
        CropRegistry registry = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(registry, Clock.fixed(NOW, ZoneOffset.UTC));
        CropProfile lettuce = registry.get("Lettuce").orElseThrow();

        var high = strategy.evaluate(reading("Lettuce", SensorType.TEMPERATURE, 22), lettuce);
        var low = strategy.evaluate(reading("Lettuce", SensorType.TEMPERATURE, 14), lettuce);

        assertThat(high.energyUsage()).isZero();
        assertThat(low.energyUsage()).isZero();
    }

    private static SensorReading reading(String crop, SensorType type, double value) {
        return new SensorReading(new ChamberId("CH1"), crop, type, value, NOW);
    }

    private static CropProfile profile(String name, ThresholdRange temperature) {
        return CropProfile.builder(name)
                .temperature(temperature)
                .humidity(new ThresholdRange(0, Double.MAX_VALUE))
                .soilMoisture(new ThresholdRange(0, Double.MAX_VALUE))
                .light(new ThresholdRange(0, Double.MAX_VALUE))
                .nutrient(new ThresholdRange(0, Double.MAX_VALUE))
                .mitigationResourceCosts(new MitigationResourceCosts(10, 15, 0, 0))
                .build();
    }
}
