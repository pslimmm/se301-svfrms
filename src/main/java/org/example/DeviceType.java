package org.example;

/** Supported queued controls, with the names expected by the legacy activation helper. */
public enum DeviceType {
    COOLING_FAN("Cooling Fan"),
    WATER_PUMP("Water Pump"),
    NUTRIENT_INJECTOR("Nutrient Injector"),
    HUMIDIFIER("Humidifier"),
    DEHUMIDIFIER("Dehumidifier"),
    GROW_LIGHT("Grow Light");

    private final String displayName;

    DeviceType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
