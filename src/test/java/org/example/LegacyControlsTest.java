package org.example;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LegacyControlsTest extends LegacyTestSupport {
    @ParameterizedTest
    @CsvSource({
        "Cooling Fan, Temperature, 30, 25, Temperature reduced to 25.0C",
        "Cooling Fan, Temperature, 20, 18, Temperature reduced to 18.0C",
        "Cooling Fan, Temperature, 10, 18, Temperature reduced to 18.0C",
        "Water Pump, Soil, 40, 60, Soil moisture increased to 60.0%",
        "Water Pump, Soil, 95, 100, Soil moisture increased to 100.0%",
        "Nutrient Injector, Nutrient, 50, 65, Nutrient level increased to 65.0%",
        "Nutrient Injector, Nutrient, 95, 100, Nutrient level increased to 100.0%",
        "Humidifier, Humidity, 60, 70, Humidity increased to 70.0%",
        "Humidifier, Humidity, 95, 100, Humidity increased to 100.0%",
        "Dehumidifier, Humidity, 70, 60, Humidity reduced to 60.0%",
        "Dehumidifier, Humidity, 35, 30, Humidity reduced to 30.0%",
        "Dehumidifier, Humidity, 20, 30, Humidity reduced to 30.0%",
        "Grow Light, Light, 600, 750, Light level increased to 750.0 lux",
        "Grow Light, Light, 950, 1000, Light level increased to 1000.0 lux",
        "Grow Light, Light, 1100, 1000, Light level increased to 1000.0 lux"
    })
    void preservesEffectsClampsAndMessages(String control, String sensor, double initial,
                                           double expected, String resultMessage) {
        app.processSensor("CH1", "Unknown", sensor, initial);
        clearOutput();

        app.activateControl("CH1", control);

        var state = app.getChamberState("CH1");
        assertEquals(expected, reading("CH1", sensor));
        assertEquals(sensor.equals("Temperature") ? expected : 24, state.temperature());
        assertEquals(sensor.equals("Humidity") ? expected : 70, state.humidity());
        assertEquals(sensor.equals("Soil") ? expected : 60, state.soilMoisture());
        assertEquals(sensor.equals("Light") ? expected : 600, state.lightLevel());
        assertEquals(sensor.equals("Nutrient") ? expected : 70, state.nutrientLevel());
        var summary = app.getChamberSummary("CH1");
        assertEquals(1, summary.mitigationActions());
        assertEquals(control + " Activated", summary.lastAction());
        assertEquals(0, summary.alerts());
        assertEquals("None", summary.lastAlert());
        assertEquals("\t\u001B[32m[CH1][CONTROL] " + control + " Activated\u001B[0m\n"
            + "\t\u001B[32m[CH1][CONTROL] " + resultMessage + "\u001B[0m\n", output());
        assertEquals(0, app.snapshot().totalWaterUsage(), "execution does not charge usage again");
        assertEquals(0, app.snapshot().totalEnergyUsage());
        assertTrue(app.snapshot().alerts().isEmpty());
    }

    @Test
    void queuedControlsDrainInOrderAndUseStateAtExecutionTime() {
        app.processSensor("CH1", "Tomato", "Temperature", 30);
        app.processSensor("CH1", "Tomato", "Temperature", 27);
        app.addControlRequest("CH1", DeviceType.WATER_PUMP);
        assertEquals(5, SVFRMS.CONTROL_QUEUE_CAPACITY);
        assertEquals(List.of(new ControlRequest("CH1", DeviceType.COOLING_FAN),
            new ControlRequest("CH1", DeviceType.WATER_PUMP)), app.pendingControls());
        app.stopControlThread();

        // Run synchronously: characterize drain semantics without scheduling races.
        app.new ControlTask().run();

        assertTrue(app.pendingControls().isEmpty());
        assertEquals(22, reading("CH1", "Temperature"));
        assertEquals(80, reading("CH1", "Soil"));
        assertEquals(2, app.getChamberSummary("CH1").mitigationActions());
        assertEquals("Water Pump Activated", app.getChamberSummary("CH1").lastAction());
        assertTrue(output().indexOf("Cooling Fan Activated") < output().indexOf("Water Pump Activated"));
        assertEquals(10, app.snapshot().totalEnergyUsage());
        assertEquals(0, app.snapshot().totalWaterUsage());
    }

    @Test
    void unknownControlHasNoDeviceEffect() {
        app.activateControl("CH1", "Heating Unit");
        assertEquals(24, reading("CH1", "Temperature"));
        assertEquals(70, reading("CH1", "Humidity"));
        assertEquals(60, reading("CH1", "Soil"));
        assertEquals(600, reading("CH1", "Light"));
        assertEquals(70, reading("CH1", "Nutrient"));
        assertTrue(app.snapshot().chamberSummaries().isEmpty());
        assertEquals("", output());
    }

    @Test
    void cycleHelpersOnlyAccumulateUsage() {
        app.irrigationCycle();
        app.irrigationCycle();
        app.feedingCycle();
        app.feedingCycle();
        assertEquals(200, app.snapshot().totalWaterUsage());
        assertEquals(40, app.snapshot().totalEnergyUsage());
        assertTrue(app.snapshot().chamberStates().isEmpty());
        assertTrue(app.snapshot().chamberSummaries().isEmpty());
        assertTrue(app.snapshot().alerts().isEmpty());
        assertTrue(app.pendingControls().isEmpty());
        assertEquals("", output());
    }

    @Test
    void chamberPerformsTwentyOrderedIterationsAndChargesBaselineUsage() {
        int[] bounds = {30, 60, 60, 900, 80};
        int[] samples = {14, 30, 40, 300, 50}; // Tomato readings 24,70,60,600,70: no alerts.
        int[] calls = {0};
        Random scriptedRandom = new Random(301) {
            @Override
            public int nextInt(int bound) {
                int index = calls[0]++ % 5;
                assertEquals(bounds[index], bound);
                return samples[index];
            }
        };
        app = new SVFRMS(scriptedRandom, capturedOut::println);
        app.new ChamberTask("CH1", "Tomato").run();

        assertEquals(100, calls[0]);
        assertEquals(100, app.snapshot().sensorLogs().size());
        List<String> sequence = List.of("Temperature = 24.0", "Humidity = 70.0", "Soil = 60.0",
            "Light = 600.0", "Nutrient = 70.0");
        for (int i = 0; i < 100; i++) {
            assertTrue(app.snapshot().sensorLogs().get(i).endsWith(" : Processing Tomato " + sequence.get(i % 5)));
        }
        assertEquals(40, app.snapshot().totalWaterUsage());
        assertEquals(20, app.snapshot().totalEnergyUsage());
        assertTrue(app.snapshot().alerts().isEmpty());
        assertTrue(app.pendingControls().isEmpty());
    }
}
