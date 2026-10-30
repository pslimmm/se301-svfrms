package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/**
 * Raises temperature by 5 °C, up to 35 °C. Listed in the brief but not in the legacy code, so no
 * rule triggers it by default. Add {@code response.temperature.low=Heating Unit} to facility.properties to use it.
 */
public final class HeatingUnit implements Device {

    @Override
    public String name() {
        return "Heating Unit";
    }

    @Override
    public String activate(ChamberState state) {
        double temperature = state.adjust(SensorType.TEMPERATURE, t -> Math.min(35, t + 5));
        return "Temperature increased to " + temperature + "C";
    }
}
