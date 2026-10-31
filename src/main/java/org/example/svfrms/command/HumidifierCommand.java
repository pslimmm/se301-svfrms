package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class HumidifierCommand extends AbstractDeviceCommand {
    private static final double MAX_HUMIDITY_PERCENT = 100;
    private static final double HUMIDITY_INCREASE_PERCENT = 10;

    public HumidifierCommand() {
        super(ActuatorType.HUMIDIFIER, "Humidifier Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), Math.min(MAX_HUMIDITY_PERCENT, state.humidity() + HUMIDITY_INCREASE_PERCENT),
                state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Humidity increased to " + state.humidity() + "%";
    }
}
