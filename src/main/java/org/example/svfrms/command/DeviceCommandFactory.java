package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;

/** One construction mapping shared by automatic and manually requested controls. */
public final class DeviceCommandFactory {
    private DeviceCommandFactory() { }

    public static DeviceCommand create(ActuatorType type) {
        return switch (type) {
            case COOLING_FAN -> new CoolingFanCommand();
            case WATER_PUMP -> new WaterPumpCommand();
            case NUTRIENT_INJECTOR -> new NutrientInjectorCommand();
            case HUMIDIFIER -> new HumidifierCommand();
            case DEHUMIDIFIER -> new DehumidifierCommand();
            case GROW_LIGHT -> new GrowLightCommand();
        };
    }
}
