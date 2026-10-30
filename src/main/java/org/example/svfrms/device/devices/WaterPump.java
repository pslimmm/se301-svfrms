package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Irrigates: raises soil moisture by 20 %, up to 100 %. */
public final class WaterPump implements Device {

    @Override
    public String name() {
        return "Water Pump";
    }

    @Override
    public String activate(ChamberState state) {
        double moisture = state.adjust(SensorType.SOIL_MOISTURE, m -> Math.min(100, m + 20));
        return "Soil moisture increased to " + moisture + "%";
    }
}
