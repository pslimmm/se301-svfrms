package org.example.svfrms.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.chamber.ChamberStateStore;
import org.example.svfrms.config.ConfigFile;
import org.example.svfrms.device.devices.CoolingFan;
import org.example.svfrms.device.devices.Dehumidifier;
import org.example.svfrms.device.devices.GrowLight;
import org.example.svfrms.device.devices.HeatingUnit;
import org.example.svfrms.device.devices.Humidifier;
import org.example.svfrms.device.devices.NutrientInjector;
import org.example.svfrms.device.devices.ShadeController;
import org.example.svfrms.device.devices.VentilationSystem;
import org.example.svfrms.device.devices.WaterPump;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.SensorType;
import org.junit.jupiter.api.Test;

class DevicesTest {

    private static ChamberState stateWith(SensorType type, double value) {
        ChamberState state = new ChamberState();
        state.set(type, value);
        return state;
    }

    @Test
    void legacyDevicesKeepTheirEffectsAndLimits() {
        assertEquals("Temperature reduced to 25.0C", new CoolingFan().activate(stateWith(SensorType.TEMPERATURE, 30)));
        assertEquals("Temperature reduced to 18.0C", new CoolingFan().activate(stateWith(SensorType.TEMPERATURE, 20)));
        assertEquals("Soil moisture increased to 100.0%", new WaterPump().activate(stateWith(SensorType.SOIL_MOISTURE, 95)));
        assertEquals("Nutrient level increased to 45.0%", new NutrientInjector().activate(stateWith(SensorType.NUTRIENT, 30)));
        assertEquals("Humidity increased to 69.0%", new Humidifier().activate(stateWith(SensorType.HUMIDITY, 59)));
        assertEquals("Humidity reduced to 30.0%", new Dehumidifier().activate(stateWith(SensorType.HUMIDITY, 35)));
        assertEquals("Light level increased to 1000.0 lux", new GrowLight().activate(stateWith(SensorType.LIGHT, 900)));
    }

    @Test
    void briefDevicesWork() {
        assertEquals("Temperature increased to 35.0C", new HeatingUnit().activate(stateWith(SensorType.TEMPERATURE, 33)));
        assertEquals("Humidity reduced to 75.0%", new VentilationSystem().activate(stateWith(SensorType.HUMIDITY, 80)));
        assertEquals("Light level reduced to 0.0 lux", new ShadeController().activate(stateWith(SensorType.LIGHT, 100)));
    }

    @Test
    void controllerRunsDeviceAndNotifiesListeners() {
        DeviceRegistry registry = new DeviceRegistry();
        registry.register(new CoolingFan());
        ChamberStateStore states = new ChamberStateStore();
        DeviceController controller = new DeviceController(registry, states);
        List<DeviceActivation> heard = new ArrayList<>();
        controller.addListener(heard::add);

        controller.activate("CH1", "Cooling Fan");
        controller.activate("CH1", "Teleporter"); // unknown devices are ignored, like legacy

        assertEquals(List.of(new DeviceActivation("CH1", "Cooling Fan", "Temperature reduced to 19.0C")), heard);
        assertEquals(19.0, states.stateOf("CH1").get(SensorType.TEMPERATURE));
    }

    @Test
    void bundledResponsePlanMatchesLegacyDeviceChoices() {
        ResponsePlan plan = ResponsePlan.fromConfig(ConfigFile.fromClasspath("facility.properties"));
        assertEquals(Optional.of("Cooling Fan"), plan.deviceFor(SensorType.TEMPERATURE, Breach.HIGH));
        assertEquals(Optional.empty(), plan.deviceFor(SensorType.TEMPERATURE, Breach.LOW));
        assertEquals(Optional.of("Dehumidifier"), plan.deviceFor(SensorType.HUMIDITY, Breach.HIGH));
        assertEquals(Optional.of("Humidifier"), plan.deviceFor(SensorType.HUMIDITY, Breach.LOW));
        assertEquals(Optional.of("Water Pump"), plan.deviceFor(SensorType.SOIL_MOISTURE, Breach.LOW));
        assertEquals(Optional.of("Grow Light"), plan.deviceFor(SensorType.LIGHT, Breach.LOW));
        assertEquals(Optional.of("Nutrient Injector"), plan.deviceFor(SensorType.NUTRIENT, Breach.LOW));
        assertTrue(plan.deviceFor(SensorType.HUMIDITY, Breach.INVALID).isEmpty());
    }
}
