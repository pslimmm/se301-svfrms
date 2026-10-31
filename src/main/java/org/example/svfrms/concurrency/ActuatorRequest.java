package org.example.svfrms.concurrency;

import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.command.StopActuatorCommand;
import org.example.svfrms.domain.chamber.ChamberId;

import java.util.Objects;

/** Typed work item retaining the chamber identity lost by the legacy string queue. */
public record ActuatorRequest(ChamberId chamberId, DeviceCommand command) {
    public ActuatorRequest {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(command, "command");
    }

    public static ActuatorRequest stopSignal() {
        return new ActuatorRequest(new ChamberId("__STOP__"), new StopActuatorCommand());
    }
}
