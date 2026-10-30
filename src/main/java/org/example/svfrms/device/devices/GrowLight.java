package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Adds 150 lux of artificial light, up to 1000 lux. */
public final class GrowLight implements Device {

    @Override
    public String name() {
        return "Grow Light";
    }

    @Override
    public String activate(ChamberState state) {
        double light = state.adjust(SensorType.LIGHT, l -> Math.min(1000, l + 150));
        return "Light level increased to " + light + " lux";
    }
}
