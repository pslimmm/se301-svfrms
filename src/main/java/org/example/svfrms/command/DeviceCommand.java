package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

/**
 * GoF Command interface encapsulating an executable actuator operation against a chamber.
 */
public interface DeviceCommand {

    ChamberId getChamberId();

    ActuatorType getActuatorType();

    void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics);

    default boolean isPoisonPill() {
        return false;
    }
}
