package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;

public final class GrowLightCommand extends AbstractDeviceCommand {
    public GrowLightCommand() {
        super(ActuatorType.GROW_LIGHT, "Grow Light Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                state.soilMoisture(), Math.min(1000, state.lightLevel() + 150), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Light level increased to " + state.lightLevel() + " lux";
    }
}
