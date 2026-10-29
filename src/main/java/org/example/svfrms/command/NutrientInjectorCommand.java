package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public class NutrientInjectorCommand extends AbstractDeviceCommand {

    public NutrientInjectorCommand(ChamberId chamberId) {
        super(chamberId, ActuatorType.NUTRIENT_INJECTOR);
    }

    @Override
    public void execute(ChamberStateTracker stateTracker, ChamberSummaryTracker summaryTracker, ResourceAccountingService metrics) {
        ChamberSnapshot updated = stateTracker.update(state -> new ChamberSnapshot(
                state.temperature(),
                state.humidity(),
                state.soilMoisture(),
                state.lightLevel(),
                Math.min(100.0, state.nutrientLevel() + 15.0)
        ));

        if (summaryTracker != null) {
            summaryTracker.recordAction("Nutrient Injector Activated");
        }
        logControl("Nutrient Injector Activated");
        logControl("Nutrient level increased to " + updated.nutrientLevel() + "%");
    }
}
