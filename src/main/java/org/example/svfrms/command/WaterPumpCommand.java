package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class WaterPumpCommand extends AbstractDeviceCommand {
    private static final double MAX_SOIL_MOISTURE_PERCENT = 100;
    private static final double SOIL_MOISTURE_INCREASE_PERCENT = 20;

    public WaterPumpCommand() {
        super(ActuatorType.WATER_PUMP, "Water Pump Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                Math.min(MAX_SOIL_MOISTURE_PERCENT, state.soilMoisture() + SOIL_MOISTURE_INCREASE_PERCENT), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Soil moisture increased to " + state.soilMoisture() + "%";
    }
}
