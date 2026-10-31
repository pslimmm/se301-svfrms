package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;

import java.util.*;

public abstract class AbstractDeviceCommand implements DeviceCommand {
    private final ActuatorType type;
    private final String actionName;

    protected AbstractDeviceCommand(ActuatorType type, String actionName) {
        this.type = Objects.requireNonNull(type, "type");
        this.actionName = Objects.requireNonNull(actionName, "actionName");
    }

    @Override
    public final Optional<ActuatorType> type() {
        return Optional.of(type);
    }

    @Override
    public final String actionName() {
        return actionName;
    }
}
