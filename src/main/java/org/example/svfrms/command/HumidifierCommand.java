package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public class HumidifierCommand extends AbstractDeviceCommand {

    public HumidifierCommand(ChamberId chamberId) {
        super(chamberId, ActuatorType.HUMIDIFIER);
    }

    @Override
    public void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics) {
        ChamberSnapshot updated = stateTracker.update(state -> new ChamberSnapshot(
                state.temperature(),
                Math.min(100.0, state.humidity() + 10.0),
                state.soilMoisture(),
                state.lightLevel(),
                state.nutrientLevel()
        ));

        if (summaryTracker != null) {
            summaryTracker.recordAction("Humidifier Activated");
        }
        logControl("Humidifier Activated");
        logControl("Humidity increased to " + updated.humidity() + "%");
    }
}
