package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.example.DeviceType.*;
import static org.example.SensorType.*;
import static org.example.ThresholdRule.Direction.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluatorTest {
    private final RuleEvaluator evaluator = new RuleEvaluator(CropCatalog.legacyDefaults());

    @ParameterizedTest(name = "{0}, reading={1}, alert={2}")
    @MethodSource("org.example.LegacySensorRulesTest#boundaryCases")
    void pureEvaluationMatchesTheIndependentLegacyBoundaryTable(
            LegacySensorRulesTest.Rule rule, double value, boolean alert) {
        List<RuleEvaluator.Effect> effects = evaluator.evaluate("CH1", rule.crop(), rule.sensor(), value);
        List<RuleEvaluator.Effect> expected = alert ? List.of(new RuleEvaluator.Effect(
            "CH1 " + rule.message(), rule.control() == null ? null : new ControlRequest("CH1", rule.control()),
            rule.water(), rule.energy())) : List.of();
        assertEquals(expected, effects);
        assertEquals(expected, evaluator.evaluate("CH1", rule.crop(), rule.sensor(), value),
            "evaluation must not remember or deduplicate an earlier reading");
        assertThrows(UnsupportedOperationException.class, () -> effects.add(
            new RuleEvaluator.Effect("injected", null, 0, 0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"temperature", "soil", "Light ", "Unknown"})
    void unknownSensorNamesHaveNoRules(String sensor) {
        assertTrue(evaluator.evaluate("CH1", "Tomato", sensor, 0).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = SensorType.class, names = {"LIGHT", "NUTRIENT"})
    void globalRulesStillApplyWhenTheCropIsNotRegistered(SensorType sensor) {
        String suffix = sensor == LIGHT ? "Low Light Level" : "Low Nutrient Level";
        DeviceType device = sensor == LIGHT ? GROW_LIGHT : NUTRIENT_INJECTOR;
        assertEquals(List.of(new RuleEvaluator.Effect("CH|1 " + suffix,
            new ControlRequest("CH|1", device), 0, 0)),
            evaluator.evaluate("CH|1", "Unregistered", sensor.legacyName(), 0));
    }

    @ParameterizedTest
    @EnumSource(SensorType.class)
    void extractionDoesNotIntroduceNanSensorFailureAlerts(SensorType sensor) {
        assertTrue(evaluator.evaluate("CH1", "Tomato", sensor.legacyName(), Double.NaN).isEmpty());
    }

    @Test
    void explicitCropAndGlobalRulesAreAdditiveInStableOrder() {
        CropProfile profile = new CropProfile("Fixture", List.of(
            new ThresholdRule(LIGHT, BELOW, 600, "Dim", GROW_LIGHT, 2, 3)));
        CropCatalog catalog = new CropCatalog(List.of(profile), List.of(
            new ThresholdRule(LIGHT, BELOW, 500, "Low Light Level", null, 0, 4)), List.of());
        var expected = List.of(
            new RuleEvaluator.Effect("CH1 Fixture Dim", new ControlRequest("CH1", GROW_LIGHT), 2, 3),
            new RuleEvaluator.Effect("CH1 Low Light Level", null, 0, 4));
        assertEquals(expected, new RuleEvaluator(catalog).evaluate("CH1", "Fixture", "Light", 450));

        FarmState state = new FarmState(catalog);
        var result = state.processSensor("CH1", "Fixture", "Light", 450);
        assertEquals(List.of("CH1 Fixture Dim", "CH1 Low Light Level"), result.alerts());
        assertEquals(List.of(new ControlRequest("CH1", GROW_LIGHT)), result.controls());
        var snapshot = state.snapshot();
        assertEquals(450, snapshot.chamberStates().get("CH1").lightLevel());
        assertEquals(2, snapshot.totalAlertsGenerated());
        assertEquals(2, snapshot.chamberSummaries().get("CH1").alerts());
        assertEquals(2, snapshot.totalWaterUsage());
        assertEquals(7, snapshot.totalEnergyUsage());
    }

    @Test
    void oneEvaluatorCanBeSharedAcrossConcurrentCallersWithoutRetainingTheirReadings() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                String chamber = "CH" + worker;
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int i = 0; i < 100; i++) {
                        assertEquals(List.of(new RuleEvaluator.Effect(chamber + " Tomato Low Temp", null, 0, 15)),
                            evaluator.evaluate(chamber, "Tomato", "Temperature", 20));
                        assertTrue(evaluator.evaluate(chamber, "Tomato", "Temperature", 25).isEmpty());
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
