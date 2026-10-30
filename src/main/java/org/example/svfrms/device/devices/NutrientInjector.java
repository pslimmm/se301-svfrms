package org.example.svfrms.device.devices;

import org.example.svfrms.chamber.ChamberState;
import org.example.svfrms.device.Device;
import org.example.svfrms.model.SensorType;

/** Raises nutrient level by 15 %, up to 100 %. */
public final class NutrientInjector implements Device {

    @Override
    public String name() {
        return "Nutrient Injector";
    }

    @Override
    public String activate(ChamberState state) {
        double level = state.adjust(SensorType.NUTRIENT, n -> Math.min(100, n + 15));
        return "Nutrient level increased to " + level + "%";
    }
}
