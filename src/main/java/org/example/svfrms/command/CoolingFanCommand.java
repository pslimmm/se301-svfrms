package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public final class CoolingFanCommand extends AbstractDeviceCommand {
    private final long energyUsage;

    public CoolingFanCommand() {
        this(10);
    }

    public CoolingFanCommand(long energyUsage) {
        super(ActuatorType.COOLING_FAN, "Cooling Fan Activated");
        if (energyUsage < 0) throw new IllegalArgumentException("energyUsage must be non-negative");
        this.energyUsage = energyUsage;
    }

    @Override
    public void execute(ChamberStateTracker tracker, ResourceAccountingService metrics) {
        tracker.updateAndGet(state -> new ChamberSnapshot(Math.max(18, state.temperature() - 5),
                state.humidity(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
        metrics.recordEnergy(energyUsage);
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Temperature reduced to " + state.temperature() + "C";
    }
}
