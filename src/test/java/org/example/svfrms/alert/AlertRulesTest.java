package org.example.svfrms.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.svfrms.crop.CropProfile;
import org.example.svfrms.crop.CropThreshold;
import org.example.svfrms.crop.InMemoryCropRegistry;
import org.example.svfrms.model.Alert;
import org.example.svfrms.model.AlertSeverity;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.Range;
import org.example.svfrms.model.ResourceCost;
import org.example.svfrms.model.SensorReading;
import org.example.svfrms.model.SensorType;
import org.junit.jupiter.api.Test;

class AlertRulesTest {

    private final InMemoryCropRegistry crops = new InMemoryCropRegistry();
    private final CropThresholdRule cropRule = new CropThresholdRule(crops);

    AlertRulesTest() {
        crops.register(new CropProfile("Tomato", Map.of(SensorType.TEMPERATURE,
                new CropThreshold(Range.between(22, 28), ResourceCost.NONE, ResourceCost.NONE))));
    }

    private static SensorReading reading(String crop, SensorType type, double value) {
        return new SensorReading("CH1", crop, type, value);
    }

    @Test
    void cropRuleUsesStrictBoundsLikeLegacy() {
        assertTrue(cropRule.evaluate(reading("Tomato", SensorType.TEMPERATURE, 28)).isEmpty());
        assertTrue(cropRule.evaluate(reading("Tomato", SensorType.TEMPERATURE, 22)).isEmpty());
        assertEquals("CH1 Tomato High Temp", message(cropRule.evaluate(reading("Tomato", SensorType.TEMPERATURE, 28.1))));
        assertEquals("CH1 Tomato Low Temp", message(cropRule.evaluate(reading("Tomato", SensorType.TEMPERATURE, 21.9))));
    }

    @Test
    void cropRuleIgnoresSensorsWithoutAThresholdAndUnknownCrops() {
        assertTrue(cropRule.evaluate(reading("Tomato", SensorType.HUMIDITY, 5)).isEmpty());
        assertTrue(cropRule.evaluate(reading("Mystery", SensorType.TEMPERATURE, 99)).isEmpty());
    }

    @Test
    void newlyRegisteredCropIsCheckedWithoutCodeChanges() {
        crops.register(new CropProfile("Kale", Map.of(SensorType.TEMPERATURE,
                new CropThreshold(Range.between(15, 24), ResourceCost.NONE, ResourceCost.NONE))));
        assertEquals("CH1 Kale High Temp", message(cropRule.evaluate(reading("Kale", SensorType.TEMPERATURE, 30))));
    }

    @Test
    void facilityRulesComeFromConfigAndApplyToAnyCrop() {
        List<AlertRule> rules = FacilityThresholdRule.fromConfig(Map.of(
                "threshold.light.min", "500", "threshold.nutrient.min", "40"));
        assertEquals(2, rules.size());
        Optional<Alert> light = rules.getFirst().evaluate(reading("Mystery", SensorType.LIGHT, 499));
        assertEquals("CH1 Low Light Level", message(light));
        assertTrue(rules.getFirst().evaluate(reading("Mystery", SensorType.LIGHT, 500)).isEmpty());
        assertTrue(rules.getFirst().evaluate(reading("Mystery", SensorType.NUTRIENT, 1)).isEmpty());
        assertEquals("CH1 Low Nutrient Level", message(rules.get(1).evaluate(reading("Tomato", SensorType.NUTRIENT, 39))));
    }

    @Test
    void sensorFailureIsCritical() {
        SensorFailureRule rule = new SensorFailureRule();
        Alert alert = rule.evaluate(reading("Tomato", SensorType.HUMIDITY, Double.NaN)).orElseThrow();
        assertEquals("CH1 Humidity Sensor Failure", alert.message());
        assertEquals(AlertSeverity.CRITICAL, alert.severity());
        assertEquals(Breach.INVALID, alert.breach());
        assertTrue(rule.evaluate(reading("Tomato", SensorType.HUMIDITY, 50)).isEmpty());
        assertTrue(cropRule.evaluate(reading("Tomato", SensorType.TEMPERATURE, Double.NaN)).isEmpty());
    }

    @Test
    void alertServiceNotifiesListenersAndArchives() {
        AlertService service = new AlertService();
        StringBuilder heard = new StringBuilder();
        service.addListener(a -> heard.append(a.message()));
        Alert alert = cropRule.evaluate(reading("Tomato", SensorType.TEMPERATURE, 40)).orElseThrow();
        service.raise(alert);
        assertEquals("CH1 Tomato High Temp", heard.toString());
        assertEquals(1, service.archiveActive());
        assertEquals(0, service.archiveActive());
        assertEquals(1, service.snapshot().archived().size());
        assertEquals(1, service.snapshot().totalGenerated());
    }

    private static String message(Optional<Alert> alert) {
        return alert.orElseThrow().message();
    }
}
