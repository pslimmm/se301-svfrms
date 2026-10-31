package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;

public final class NutrientInjectorCommand extends AbstractDeviceCommand {
    private static final double MAX_NUTRIENT_LEVEL_PERCENT = 100;
    private static final double NUTRIENT_LEVEL_INCREASE_PERCENT = 15;

    public NutrientInjectorCommand() {
        super(ActuatorType.NUTRIENT_INJECTOR, "Nutrient Injector Activated");
    }

    @Override
    public ChamberSnapshot execute(ChamberStateTracker tracker) {
        return tracker.updateAndGet(state -> new ChamberSnapshot(state.temperature(), state.humidity(),
                state.soilMoisture(), state.lightLevel(), Math.min(MAX_NUTRIENT_LEVEL_PERCENT, state.nutrientLevel() + NUTRIENT_LEVEL_INCREASE_PERCENT)));
    }

    @Override
    public String stateDescription(ChamberSnapshot state) {
        return "Nutrient level increased to " + state.nutrientLevel() + "%";
    }
}
