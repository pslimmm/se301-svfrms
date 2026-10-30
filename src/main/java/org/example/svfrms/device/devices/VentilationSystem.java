package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/**
 * Improves air circulation: lowers humidity by 5 %, never below 30 %. From the brief and not wired to
 * any rule by default.
 */
public final class VentilationSystem implements Device {

    @Override
    public String name() {
        return "Ventilation System";
    }

    @Override
    public String activate(ChamberState state) {
        double humidity = state.adjust(SensorType.HUMIDITY, h -> Math.max(30, h - 5));
        return "Humidity reduced to " + humidity + "%";
    }
}
