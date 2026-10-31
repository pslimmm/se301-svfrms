package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class CoolingFanCommand extends AbstractDeviceCommand {
    private static final double MIN_TEMPERATURE_CELSIUS = 18;
    private static final double TEMPERATURE_REDUCTION_CELSIUS = 5;

    public CoolingFanCommand() {
        super(ActuatorType.COOLING_FAN, "Cooling Fan Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(Math.max(MIN_TEMPERATURE_CELSIUS, state.temperature() - TEMPERATURE_REDUCTION_CELSIUS),
                state.humidity(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Temperature reduced to " + state.temperature() + "C";
    }
}
