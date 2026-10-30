package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Reduces light by 150 lux, never below 0. From the brief and not wired to any rule by default. */
public final class ShadeController implements Device {

    @Override
    public String name() {
        return "Shade Controller";
    }

    @Override
    public String activate(ChamberState state) {
        double light = state.adjust(SensorType.LIGHT, l -> Math.max(0, l - 150));
        return "Light level reduced to " + light + " lux";
    }
}
