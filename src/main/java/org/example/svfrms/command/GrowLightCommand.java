package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public class GrowLightCommand extends AbstractDeviceCommand {

    public GrowLightCommand(ChamberId chamberId) {
        super(chamberId, ActuatorType.GROW_LIGHT);
    }

    @Override
    public void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics) {
        ChamberSnapshot updated = stateTracker.update(state -> new ChamberSnapshot(
                state.temperature(),
                state.humidity(),
                state.soilMoisture(),
                Math.min(1000.0, state.lightLevel() + 150.0),
                state.nutrientLevel()
        ));

        if (summaryTracker != null) {
            summaryTracker.recordAction("Grow Light Activated");
        }
        logControl("Grow Light Activated");
        logControl("Light level increased to " + updated.lightLevel() + " lux");
    }
}
