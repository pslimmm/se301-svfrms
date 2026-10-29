package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.util.Objects;

/**
 * Base command providing standard metadata and summary action tracking.
 */
public abstract class AbstractDeviceCommand implements DeviceCommand {

    private final ChamberId chamberId;
    private final ActuatorType actuatorType;

    protected AbstractDeviceCommand(ChamberId chamberId, ActuatorType actuatorType) {
        this.chamberId = Objects.requireNonNull(chamberId, "chamberId must not be null");
        this.actuatorType = Objects.requireNonNull(actuatorType, "actuatorType must not be null");
    }

    @Override
    public ChamberId getChamberId() {
        return chamberId;
    }

    @Override
    public ActuatorType getActuatorType() {
        return actuatorType;
    }

    protected void logControl(String message) {
        System.out.println("\t\u001B[32m[" + chamberId + "][CONTROL] " + message + "\u001B[0m");
    }
}
