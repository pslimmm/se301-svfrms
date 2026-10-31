package org.example.svfrms.command;

import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.actuator.ActuatorType;

import java.util.Optional;

/** Queue sentinel used to stop the actuator consumer after pending work drains. */
public final class StopActuatorCommand implements DeviceCommand {
    @Override
    public Optional<ActuatorType> type() {
        return Optional.empty();
    }

    @Override
    public String actionName() {
        return "Stop";
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) { return tracker.snapshot(); }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "";
    }

    @Override
    public boolean isPoisonPill() {
        return true;
    }
}
