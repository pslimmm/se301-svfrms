package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.sensor.SensorReading;

import java.util.Map;
import java.util.Objects;

/** Applies sensor readings to the shared chamber snapshots. */
public final class ChamberStateUpdater {
    private final Map<ChamberId, ChamberStateTracker> chamberStates;

    public ChamberStateUpdater(Map<ChamberId, ChamberStateTracker> chamberStates) {
        this.chamberStates = Objects.requireNonNull(chamberStates, "chamberStates");
    }

    public void update(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        ChamberStateTracker tracker = chamberStates.get(reading.chamberId());
        if (tracker == null) tracker = chamberStates.computeIfAbsent(reading.chamberId(), ignored -> ChamberStateTracker.initial());
        updateState(tracker, reading);
    }

    private static void updateState(ChamberStateTracker tracker, SensorReading reading) {
        tracker.updateAndGet(state -> switch (reading.sensorType()) {
            case TEMPERATURE -> new ChamberSnapshot(reading.value(), state.humidity(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel());
            case HUMIDITY -> new ChamberSnapshot(state.temperature(), reading.value(), state.soilMoisture(), state.lightLevel(), state.nutrientLevel());
            case SOIL_MOISTURE -> new ChamberSnapshot(state.temperature(), state.humidity(), reading.value(), state.lightLevel(), state.nutrientLevel());
            case LIGHT -> new ChamberSnapshot(state.temperature(), state.humidity(), state.soilMoisture(), reading.value(), state.nutrientLevel());
            case NUTRIENT -> new ChamberSnapshot(state.temperature(), state.humidity(), state.soilMoisture(), state.lightLevel(), reading.value());
        });
    }
}
