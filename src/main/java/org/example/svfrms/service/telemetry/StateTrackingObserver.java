package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.sensor.SensorReading;

import java.util.Map;
import java.util.Objects;

/**
 * Telemetry observer updating current chamber state snapshots as readings arrive.
 */
public class StateTrackingObserver implements TelemetryObserver {

    private final Map<ChamberId, ChamberStateTracker> stateMap;

    public StateTrackingObserver(Map<ChamberId, ChamberStateTracker> stateMap) {
        this.stateMap = Objects.requireNonNull(stateMap, "stateMap must not be null");
    }

    @Override
    public void onTelemetry(SensorReading reading) {
        ChamberStateTracker tracker = stateMap.computeIfAbsent(reading.chamberId(), ChamberStateTracker::new);
        tracker.update(state -> switch (reading.sensorType()) {
            case TEMPERATURE -> new ChamberSnapshot(reading.value(), state.humidity(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel());
            case HUMIDITY -> new ChamberSnapshot(state.temperature(), reading.value(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel());
            case SOIL_MOISTURE -> new ChamberSnapshot(state.temperature(), state.humidity(), reading.value(), state.lightLevel(), state.nutrientLevel());
            case LIGHT -> new ChamberSnapshot(state.temperature(), state.humidity(), state.soilMoisture(), reading.value(), state.nutrientLevel());
            case NUTRIENT -> new ChamberSnapshot(state.temperature(), state.humidity(), state.soilMoisture(), state.lightLevel(), reading.value());
        });
    }
}
