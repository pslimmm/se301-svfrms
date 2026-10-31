package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class DehumidifierCommand extends AbstractDeviceCommand {
    private static final double MIN_HUMIDITY_PERCENT = 30;
    private static final double HUMIDITY_REDUCTION_PERCENT = 10;

    public DehumidifierCommand() {
        super(ActuatorType.DEHUMIDIFIER, "Dehumidifier Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), Math.max(MIN_HUMIDITY_PERCENT, state.humidity() - HUMIDITY_REDUCTION_PERCENT),
                state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Humidity reduced to " + state.humidity() + "%";
    }
}
