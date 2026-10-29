package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public class CoolingFanCommand extends AbstractDeviceCommand {

    public CoolingFanCommand(ChamberId chamberId) {
        super(chamberId, ActuatorType.COOLING_FAN);
    }

    @Override
    public void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics) {
        ChamberSnapshot updated = stateTracker.update(state -> new ChamberSnapshot(
                Math.max(18.0, state.temperature() - 5.0),
                state.humidity(),
                state.soilMoisture(),
                state.lightLevel(),
                state.nutrientLevel()
        ));

        if (summaryTracker != null) {
            summaryTracker.recordAction("Cooling Fan Activated");
        }
        logControl("Cooling Fan Activated");
        logControl("Temperature reduced to " + updated.temperature() + "C");
    }
}
