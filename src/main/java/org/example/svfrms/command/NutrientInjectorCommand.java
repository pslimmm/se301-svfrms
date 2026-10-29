package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

public final class NutrientInjectorCommand extends AbstractDeviceCommand {
    public NutrientInjectorCommand() {
        super(ActuatorType.NUTRIENT_INJECTOR, "Nutrient Injector Activated");
    }

    @Override
    public void execute(ChamberStateTracker tracker, ResourceAccountingService metrics) {
        tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                state.soilMoisture(), state.lightLevel(), Math.min(100, state.nutrientLevel() + 15)));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Nutrient level increased to " + state.nutrientLevel() + "%";
    }
}
