package org.example.svfrms.service.telemetry;

import org.example.svfrms.command.*;
import org.example.svfrms.concurrency.ActuatorRequest;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.crop.EvaluationResult;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Converts mitigation decisions into queued actuator commands. */
public final class MitigationDispatcher {
    private final ActuatorExecutionService actuators;
    private final Duration enqueueTimeout;

    public MitigationDispatcher(ActuatorExecutionService actuators, Duration enqueueTimeout) {
        this.actuators = Objects.requireNonNull(actuators, "actuators");
        this.enqueueTimeout = Objects.requireNonNull(enqueueTimeout, "enqueueTimeout");
        if (enqueueTimeout.isNegative()) {
            throw new IllegalArgumentException("enqueueTimeout must not be negative");
        }
    }

    public void dispatch(SensorReading reading, EvaluationResult result) {
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(result, "result");
        Optional<ActuatorType> mitigation = result.mitigation();
        if (mitigation.isEmpty()) {
            return;
        }
        DeviceCommand command = DeviceCommandFactory.create(mitigation.get());
        try {
            boolean accepted = actuators.submit(new ActuatorRequest(reading.chamberId(), command), enqueueTimeout);
            if (!accepted) {
                throw new IllegalStateException("Actuator queue remained full until timeout");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queuing actuator work", interrupted);
        }
    }
}
