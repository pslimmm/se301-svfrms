package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

/**
 * Sentinel command indicating that the actuator consumer worker should shut down immediately.
 */
public class PoisonPillCommand implements DeviceCommand {

    @Override
    public ChamberId getChamberId() {
        return ChamberId.of("POISON_PILL");
    }

    @Override
    public ActuatorType getActuatorType() {
        return null;
    }

    @Override
    public void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics) {
        // No-op
    }

    @Override
    public boolean isPoisonPill() {
        return true;
    }
}
