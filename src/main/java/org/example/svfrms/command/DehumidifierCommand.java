package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public final class DehumidifierCommand extends AbstractDeviceCommand {
    public DehumidifierCommand() {
        super(ActuatorType.DEHUMIDIFIER, "Dehumidifier Activated");
    }

    @Override
    public void execute(ChamberStateTracker tracker, ResourceAccountingService metrics) {
        tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), Math.max(30, state.humidity() - 10),
                state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Humidity reduced to " + state.humidity() + "%";
    }
}
