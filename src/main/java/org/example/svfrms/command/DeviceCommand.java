package org.example.svfrms.command;

import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.util.Optional;

/** A typed, executable actuator request. */
public interface DeviceCommand {
    Optional<ActuatorType> type();

    String actionName();

    void execute(ChamberStateTracker tracker, ResourceAccountingService metrics);

    String stateDescription(ChamberSnapshot state);

    default boolean isPoisonPill() {
        return false;
    }
}
