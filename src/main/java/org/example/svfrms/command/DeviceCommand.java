package org.example.svfrms.command;

import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.actuator.ActuatorType;

import java.util.Optional;

/** A typed, executable actuator request. */
public interface DeviceCommand {
    Optional<ActuatorType> type();

    String actionName();

    ChamberSnapshot execute(ChamberStateTracker tracker);

    String stateDescription(ChamberSnapshot state);

    default boolean isPoisonPill() {
        return false;
    }
}
