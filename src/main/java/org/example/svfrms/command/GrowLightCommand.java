package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class GrowLightCommand extends AbstractDeviceCommand {
    private static final double MAX_LIGHT_LUX = 1000;
    private static final double LIGHT_INCREASE_LUX = 150;

    public GrowLightCommand() {
        super(ActuatorType.GROW_LIGHT, "Grow Light Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                state.soilMoisture(), Math.min(MAX_LIGHT_LUX, state.lightLevel() + LIGHT_INCREASE_LUX), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Light level increased to " + state.lightLevel() + " lux";
    }
}
