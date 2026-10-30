package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Raises humidity by 10 %, up to 100 %. */
public final class Humidifier implements Device {

    @Override
    public String name() {
        return "Humidifier";
    }

    @Override
    public String activate(ChamberState state) {
        double humidity = state.adjust(SensorType.HUMIDITY, h -> Math.min(100, h + 10));
        return "Humidity increased to " + humidity + "%";
    }
}
