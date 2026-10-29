package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public final class HumidifierCommand extends AbstractDeviceCommand {
    private final long waterUsage;

    public HumidifierCommand() {
        this(20);
    }

    public HumidifierCommand(long waterUsage) {
        super(ActuatorType.HUMIDIFIER, "Humidifier Activated");
        if (waterUsage < 0) throw new IllegalArgumentException("waterUsage must be non-negative");
        this.waterUsage = waterUsage;
    }

    @Override
    public void execute(ChamberStateTracker tracker, ResourceAccountingService metrics) {
        tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), Math.min(100, state.humidity() + 10),
                state.soilMoisture(), state.lightLevel(), state.nutrientLevel()));
        metrics.recordWater(waterUsage);
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Humidity increased to " + state.humidity() + "%";
    }
}
