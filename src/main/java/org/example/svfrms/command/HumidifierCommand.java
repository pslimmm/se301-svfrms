package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class HumidifierCommand extends AbstractDeviceCommand {
    public HumidifierCommand() {
        super(ActuatorType.HUMIDIFIER, "Humidifier Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), Math.min(100, state.humidity() + 10),
                state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Humidity increased to " + state.humidity() + "%";
    }
}
