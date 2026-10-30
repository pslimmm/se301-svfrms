package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Lowers humidity by 10 %, never below 30 %. */
public final class Dehumidifier implements Device {

    @Override
    public String name() {
        return "Dehumidifier";
    }

    @Override
    public String activate(ChamberState state) {
        double humidity = state.adjust(SensorType.HUMIDITY, h -> Math.max(30, h - 10));
        return "Humidity reduced to " + humidity + "%";
    }
}
