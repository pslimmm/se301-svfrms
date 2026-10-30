package org.example.svfrms.chamber;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import org.example.svfrms.model.SensorType;

/**
 * The current simulated conditions in one chamber.
 *
 * <p><b>Concurrency:</b> a chamber thread writes new readings while the device thread adjusts
 * the same values (legacy issue C5). Every method is {@code synchronized} on this object, so
 * {@link #adjust} is an atomic read-modify-write and every thread sees the latest value.
 * Each chamber has its own lock, so chambers never block each other.
 */
public final class ChamberState {

    private final Map<SensorType, Double> values = new EnumMap<>(SensorType.class);

    public ChamberState() {
        // starting values copied from the legacy ChamberState class
        values.put(SensorType.TEMPERATURE, 24.0);
        values.put(SensorType.HUMIDITY, 70.0);
        values.put(SensorType.SOIL_MOISTURE, 60.0);
        values.put(SensorType.LIGHT, 600.0);
        values.put(SensorType.NUTRIENT, 70.0);
    }

    public synchronized double get(SensorType type) {
        return values.get(type);
    }

    public synchronized void set(SensorType type, double value) {
        values.put(type, value);
    }

    /** Atomically applies {@code change} to the current value and returns the new value. */
    public synchronized double adjust(SensorType type, DoubleUnaryOperator change) {
        double updated = change.applyAsDouble(values.get(type));
        values.put(type, updated);
        return updated;
    }
}
