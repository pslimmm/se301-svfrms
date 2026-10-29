package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public final class WaterPumpCommand extends AbstractDeviceCommand {
    private final long waterUsage;

    public WaterPumpCommand() {
        this(50);
    }

    public WaterPumpCommand(long waterUsage) {
        super(ActuatorType.WATER_PUMP, "Water Pump Activated");
        if (waterUsage < 0) throw new IllegalArgumentException("waterUsage must be non-negative");
        this.waterUsage = waterUsage;
    }

    @Override
    public void execute(ChamberStateTracker tracker, ResourceAccountingService metrics) {
        tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                Math.min(100, state.soilMoisture() + 20), state.lightLevel(), state.nutrientLevel()));
        metrics.recordWater(waterUsage);
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Soil moisture increased to " + state.soilMoisture() + "%";
    }
}
