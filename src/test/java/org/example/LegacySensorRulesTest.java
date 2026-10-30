package org.example;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class LegacySensorRulesTest extends LegacyTestSupport {
    record Rule(String crop, String sensor, double boundary, boolean high,
                String message, DeviceType control, int water, int energy) {}

    // Independent inventory of the legacy contract, including its asymmetries.
    static Stream<Rule> rules() {
        return Stream.of(
            new Rule("Tomato", "Temperature", 28, true, "Tomato High Temp", DeviceType.COOLING_FAN, 0, 10),
            new Rule("Tomato", "Temperature", 22, false, "Tomato Low Temp", null, 0, 15),
            new Rule("Strawberry", "Temperature", 24, true, "Strawberry High Temp", DeviceType.COOLING_FAN, 0, 10),
            new Rule("Strawberry", "Temperature", 18, false, "Strawberry Low Temp", null, 0, 15),
            new Rule("Lettuce", "Temperature", 21, true, "Lettuce High Temp", DeviceType.COOLING_FAN, 0, 0),
            new Rule("Lettuce", "Temperature", 15, false, "Lettuce Low Temp", null, 0, 0),
            new Rule("Spinach", "Temperature", 20, true, "Spinach High Temp", DeviceType.COOLING_FAN, 0, 0),
            new Rule("Spinach", "Temperature", 10, false, "Spinach Low Temp", null, 0, 0),
            new Rule("Chili", "Temperature", 32, true, "Chili High Temp", DeviceType.COOLING_FAN, 0, 0),
            new Rule("Chili", "Temperature", 24, false, "Chili Low Temp", null, 0, 0),
            new Rule("Cucumber", "Temperature", 27, true, "Cucumber High Temp", DeviceType.COOLING_FAN, 0, 0),
            new Rule("Cucumber", "Temperature", 18, false, "Cucumber Low Temp", null, 0, 0),
            new Rule("Tomato", "Humidity", 80, true, "Tomato High Humidity", DeviceType.DEHUMIDIFIER, 0, 0),
            new Rule("Tomato", "Humidity", 60, false, "Tomato Low Humidity", DeviceType.HUMIDIFIER, 20, 0),
            new Rule("Strawberry", "Humidity", 85, true, "Strawberry High Humidity", DeviceType.DEHUMIDIFIER, 0, 0),
            new Rule("Strawberry", "Humidity", 70, false, "Strawberry Low Humidity", DeviceType.HUMIDIFIER, 25, 0),
            new Rule("Tomato", "Soil", 45, false, "Tomato Low Soil Moisture", DeviceType.WATER_PUMP, 50, 0),
            new Rule("Tomato", "Light", 500, false, "Low Light Level", DeviceType.GROW_LIGHT, 0, 0),
            new Rule("Tomato", "Nutrient", 40, false, "Low Nutrient Level", DeviceType.NUTRIENT_INJECTOR, 0, 0)
        );
    }

    static Stream<Arguments> boundaryCases() {
        return rules().flatMap(rule -> Stream.of(-0.5, 0.0, 0.5)
            .map(offset -> Arguments.of(rule, rule.boundary() + offset,
                rule.high() ? offset > 0 : offset < 0)));
    }

    @ParameterizedTest(name = "{0}, reading={1}, alert={2}")
    @MethodSource("boundaryCases")
    void preservesEveryRuleAndStrictBoundary(Rule rule, double value, boolean alert) {
        app.processSensor("CH1", rule.crop(), rule.sensor(), value);

        assertEquals(value, reading("CH1", rule.sensor()), "reading replaces state before control");
        assertEquals(1, app.snapshot().sensorLogs().size());
        assertTrue(app.snapshot().sensorLogs().getFirst().endsWith(
            " : Processing " + rule.crop() + " " + rule.sensor() + " = " + value));
        String message = "CH1 " + rule.message();
        assertEquals(alert ? List.of(message) : List.of(), app.snapshot().alerts());
        assertEquals(alert ? 1 : 0, app.snapshot().totalAlerts());
        assertEquals(alert ? 1 : 0, app.snapshot().totalAlertsGenerated());
        assertEquals(alert ? rule.water() : 0, app.snapshot().totalWaterUsage());
        assertEquals(alert ? rule.energy() : 0, app.snapshot().totalEnergyUsage());
        assertEquals(alert && rule.control() != null
            ? List.of(new ControlRequest("CH1", rule.control())) : List.of(), app.pendingControls());
        assertEquals(alert ? "[CH1][" + rule.crop() + "][ALERT] " + message + "\n" : "", output());
        var summary = app.getChamberSummary("CH1");
        assertEquals(alert ? 1 : 0, summary.alerts());
        assertEquals(alert ? message : "None", summary.lastAlert());
        assertEquals(0, summary.mitigationActions());
        assertEquals("No action", summary.lastAction());
    }

    static Stream<Arguments> globalRules() {
        return Stream.of("Tomato", "Strawberry", "Lettuce", "Spinach", "Chili", "Cucumber", "Unknown")
            .flatMap(crop -> Stream.of(
                Arguments.of(crop, "Light", 499, "Low Light Level", DeviceType.GROW_LIGHT),
                Arguments.of(crop, "Nutrient", 39, "Low Nutrient Level", DeviceType.NUTRIENT_INJECTOR)));
    }

    @ParameterizedTest
    @MethodSource("globalRules")
    void lightAndNutrientApplyToEveryCrop(String crop, String sensor, double value,
                                         String message, DeviceType control) {
        app.processSensor("CH1", crop, sensor, value);
        assertEquals(List.of("CH1 " + message), app.snapshot().alerts());
        assertEquals(List.of(new ControlRequest("CH1", control)), app.pendingControls());
        assertEquals("[CH1][" + crop + "][ALERT] CH1 " + message + "\n", output());
        assertEquals(0, app.snapshot().totalWaterUsage());
        assertEquals(0, app.snapshot().totalEnergyUsage());
    }

    @ParameterizedTest
    @CsvSource({
        "Lettuce, Humidity", "Spinach, Humidity", "Chili, Humidity", "Cucumber, Humidity",
        "Strawberry, Soil", "Lettuce, Soil", "Spinach, Soil", "Chili, Soil", "Cucumber, Soil",
        "Unknown, Temperature", "Unknown, Humidity", "Unknown, Soil"
    })
    void absentCropRulesStayAbsent(String crop, String sensor) {
        for (double value : new double[] {0, 200}) {
            app.processSensor("CH1", crop, sensor, value);
            assertEquals(value, reading("CH1", sensor));
        }
        assertTrue(app.snapshot().alerts().isEmpty());
        assertTrue(app.pendingControls().isEmpty());
        assertEquals(0, app.snapshot().totalAlertsGenerated());
        assertEquals(0, app.snapshot().totalWaterUsage());
        assertEquals(0, app.snapshot().totalEnergyUsage());
        assertEquals(2, app.snapshot().sensorLogs().size());
        assertEquals("", output());
    }

    @Test
    void repeatsAlertsAndChargesUsageBeforeControlExecution() {
        app.processSensor("CH1", "Tomato", "Soil", 30);
        app.processSensor("CH1", "Tomato", "Soil", 30);
        assertEquals(List.of("CH1 Tomato Low Soil Moisture", "CH1 Tomato Low Soil Moisture"), app.snapshot().alerts());
        assertEquals(2, app.snapshot().totalAlerts());
        assertEquals(2, app.snapshot().totalAlertsGenerated());
        assertEquals(2, app.getChamberSummary("CH1").alerts());
        assertEquals(100, app.snapshot().totalWaterUsage());
        assertEquals(List.of(new ControlRequest("CH1", DeviceType.WATER_PUMP),
            new ControlRequest("CH1", DeviceType.WATER_PUMP)), app.pendingControls());
        assertEquals(30, reading("CH1", "Soil"));
        assertEquals(0, app.getChamberSummary("CH1").mitigationActions());
    }

    @Test
    void unknownSensorIsLoggedWithoutChangingReadings() {
        app.processSensor("CH1", "Tomato", "soil", 0);
        assertEquals(24, reading("CH1", "Temperature"));
        assertEquals(70, reading("CH1", "Humidity"));
        assertEquals(60, reading("CH1", "Soil"));
        assertEquals(600, reading("CH1", "Light"));
        assertEquals(70, reading("CH1", "Nutrient"));
        assertTrue(app.snapshot().sensorLogs().getFirst().endsWith(" : Processing Tomato soil = 0.0"));
        assertTrue(app.snapshot().alerts().isEmpty());
        assertTrue(app.pendingControls().isEmpty());
    }
}
