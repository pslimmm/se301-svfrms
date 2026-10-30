package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Lowers temperature by 5 °C, never below 18 °C. */
public final class CoolingFan implements Device {

    @Override
    public String name() {
        return "Cooling Fan";
    }

    @Override
    public String activate(ChamberState state) {
        double temperature = state.adjust(SensorType.TEMPERATURE, t -> Math.max(18, t - 5));
        return "Temperature reduced to " + temperature + "C";
    }
}
