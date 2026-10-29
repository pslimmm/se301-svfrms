package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public class WaterPumpCommand extends AbstractDeviceCommand {

    public WaterPumpCommand(ChamberId chamberId) {
        super(chamberId, ActuatorType.WATER_PUMP);
    }

    @Override
    public void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics) {
        ChamberSnapshot updated = stateTracker.update(state -> new ChamberSnapshot(
                state.temperature(),
                state.humidity(),
                Math.min(100.0, state.soilMoisture() + 20.0),
                state.lightLevel(),
                state.nutrientLevel()
        ));

        if (summaryTracker != null) {
            summaryTracker.recordAction("Water Pump Activated");
        }
        logControl("Water Pump Activated");
        logControl("Soil moisture increased to " + updated.soilMoisture() + "%");
    }
}
