package org.example.svfrms.domain.actuator;

/**
 * Supported automated physical control devices deployed within chambers.
 */
public enum ActuatorType {
    COOLING_FAN("Cooling Fan"),
    WATER_PUMP("Water Pump"),
    NUTRIENT_INJECTOR("Nutrient Injector"),
    HUMIDIFIER("Humidifier"),
    DEHUMIDIFIER("Dehumidifier"),
    GROW_LIGHT("Grow Light");

    private final String displayName;

    ActuatorType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ActuatorType fromDisplayName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Actuator name cannot be null");
        }
        for (ActuatorType type : values()) {
            if (type.displayName.equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown actuator: " + name);
    }
}
