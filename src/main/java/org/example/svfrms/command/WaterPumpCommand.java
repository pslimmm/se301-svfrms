package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class WaterPumpCommand extends AbstractDeviceCommand {
    public WaterPumpCommand() {
        super(ActuatorType.WATER_PUMP, "Water Pump Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                Math.min(100, state.soilMoisture() + 20), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Soil moisture increased to " + state.soilMoisture() + "%";
    }
}
