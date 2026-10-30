package org.example;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ControlRequestTest extends LegacyTestSupport {
    @ParameterizedTest
    @CsvSource({
        "COOLING_FAN, Cooling Fan, Temperature, 19",
        "WATER_PUMP, Water Pump, Soil, 80",
        "NUTRIENT_INJECTOR, Nutrient Injector, Nutrient, 85",
        "HUMIDIFIER, Humidifier, Humidity, 80",
        "DEHUMIDIFIER, Dehumidifier, Humidity, 60",
        "GROW_LIGHT, Grow Light, Light, 750"
    })
    void everyTypedDeviceReachesTheExistingActivationHelper(DeviceType device, String name,
                                                            String sensor, double expected) {
        app.addControlRequest("CH1", device);
        assertEquals(1, app.pendingControls().size());
        ControlRequest request = app.pendingControls().getFirst();
        assertEquals("CH1", request.chamberId());
        assertSame(device, request.deviceType());
        assertEquals(name, device.displayName());

        drainQueue();

        assertEquals(expected, reading("CH1", sensor));
        assertEquals(1, app.getChamberSummary("CH1").mitigationActions());
        assertEquals(name + " Activated", app.getChamberSummary("CH1").lastAction());
        assertTrue(output().contains("\t\u001B[32m[CH1][CONTROL] " + name + " Activated\u001B[0m\n"));
        assertEquals(0, app.snapshot().totalWaterUsage());
        assertEquals(0, app.snapshot().totalEnergyUsage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CH|Cooling Fan", "|CH", "CH|", "CH||2", ""})
    void sensorCommandsPreserveTheWholeChamberIdentifier(String chamber) {
        app.processSensor(chamber, "Tomato", "Temperature", 30);
        assertEquals(List.of(new ControlRequest(chamber, DeviceType.COOLING_FAN)), app.pendingControls());

        drainQueue();

        assertEquals(25, reading(chamber, "Temperature"));
        assertEquals(1, app.snapshot().chamberStates().size(), "must not create a chamber from a split prefix");
        assertEquals(1, app.snapshot().chamberSummaries().size());
        assertEquals(1, app.getChamberSummary(chamber).alerts());
        assertEquals(1, app.getChamberSummary(chamber).mitigationActions());
        assertEquals(List.of(chamber + " Tomato High Temp"), app.snapshot().alerts());
        assertTrue(output().contains("[" + chamber + "][CONTROL] Cooling Fan Activated"));
    }

    @Test
    void fifoOrderIsObservableWhenControlsDoNotCommute() {
        app.processSensor("CH1", "Unknown", "Humidity", 95);
        app.addControlRequest("CH1", DeviceType.HUMIDIFIER);
        app.addControlRequest("CH2", DeviceType.WATER_PUMP);
        app.addControlRequest("CH1", DeviceType.DEHUMIDIFIER);
        assertEquals(List.of(new ControlRequest("CH1", DeviceType.HUMIDIFIER),
            new ControlRequest("CH2", DeviceType.WATER_PUMP),
            new ControlRequest("CH1", DeviceType.DEHUMIDIFIER)), app.pendingControls());

        drainQueue();

        assertEquals(90, reading("CH1", "Humidity"), "95 -> 100 -> 90; reversing commands gives 95");
        assertEquals(80, reading("CH2", "Soil"));
        assertEquals(2, app.getChamberSummary("CH1").mitigationActions());
        assertEquals(1, app.getChamberSummary("CH2").mitigationActions());
        assertEquals("Dehumidifier Activated", app.getChamberSummary("CH1").lastAction());
        assertTrue(output().indexOf("Humidifier Activated") < output().indexOf("Water Pump Activated"));
        assertTrue(output().indexOf("Water Pump Activated") < output().indexOf("Dehumidifier Activated"));
    }

    @Test
    void fivePendingRequestsAreRetainedIncludingDuplicatesAndDrainAfterShutdown() {
        assertEquals(5, FarmApplication.CONTROL_QUEUE_CAPACITY);
        for (int i = 0; i < 5; i++) {
            app.addControlRequest("CH1", DeviceType.WATER_PUMP);
        }
        assertEquals(5, app.pendingControls().size());
        assertTrue(app.snapshot().chamberStates().isEmpty(), "submission must not execute controls");

        drainQueue();

        assertEquals(5, app.getChamberSummary("CH1").mitigationActions());
        assertEquals(100, reading("CH1", "Soil"));
    }

    @Test
    void incompleteCommandsAreRejectedBeforeTheyEnterTheQueue() {
        assertThrows(NullPointerException.class, () -> new ControlRequest(null, DeviceType.WATER_PUMP));
        assertThrows(NullPointerException.class, () -> new ControlRequest("CH1", null));
        assertThrows(NullPointerException.class, () -> app.addControlRequest(null, DeviceType.WATER_PUMP));
        assertThrows(NullPointerException.class, () -> app.addControlRequest("CH1", null));
        assertTrue(app.pendingControls().isEmpty());
        assertTrue(app.snapshot().chamberStates().isEmpty());
        assertEquals("", output());
    }

    private void drainQueue() {
        // No producer runs here: preserve normal shutdown semantics without relying on scheduling.
        app.stopControlThread();
        app.new ControlTask().run();
        assertTrue(app.pendingControls().isEmpty());
    }
}
