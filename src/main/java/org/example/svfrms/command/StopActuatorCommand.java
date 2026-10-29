package org.example.svfrms.command;

import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.util.Optional;

/** Queue sentinel used to stop the actuator consumer after pending work drains. */
public final class StopActuatorCommand implements DeviceCommand {
    @Override
    public Optional<org.example.svfrms.domain.actuator.ActuatorType> type() {
        return Optional.empty();
    }

    @Override
    public String actionName() {
        return "Stop";
    }

    @Override
    public void execute(ChamberStateTracker tracker, ResourceAccountingService metrics) { }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "";
    }

    @Override
    public boolean isPoisonPill() {
        return true;
    }
}
