package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class CoolingFanCommand extends AbstractDeviceCommand {
    public CoolingFanCommand() {
        super(ActuatorType.COOLING_FAN, "Cooling Fan Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(Math.max(18, state.temperature() - 5),
                state.humidity(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Temperature reduced to " + state.temperature() + "C";
    }
}
