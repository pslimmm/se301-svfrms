package org.example.svfrms.domain.chamber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Atomic owner of a chamber's immutable state snapshot. */
public final class ChamberStateTracker {
    private static final double INITIAL_TEMPERATURE_CELSIUS = 24;
    private static final double INITIAL_HUMIDITY_PERCENT = 70;
    private static final double INITIAL_SOIL_MOISTURE_PERCENT = 60;
    private static final double INITIAL_LIGHT_LUX = 600;
    private static final double INITIAL_NUTRIENT_LEVEL_PERCENT = 70;

    private final AtomicReference<ChamberSnapshot> state;

    public ChamberStateTracker(ChamberSnapshot initialState) {
        state = new AtomicReference<>(Objects.requireNonNull(initialState, "initialState"));
    }

    public static ChamberStateTracker initial() {
        return new ChamberStateTracker(new ChamberSnapshot(INITIAL_TEMPERATURE_CELSIUS, INITIAL_HUMIDITY_PERCENT,
                INITIAL_SOIL_MOISTURE_PERCENT, INITIAL_LIGHT_LUX, INITIAL_NUTRIENT_LEVEL_PERCENT));
    }

    public ChamberSnapshot snapshot() {
        return state.get();
    }

    /** Applies an update with a compare-and-set loop so concurrent field changes are not overwritten. The callback must be pure: it may be retried. */
    public ChamberSnapshot updateAndGet(UnaryOperator<ChamberSnapshot> update) {
        Objects.requireNonNull(update, "update");
        ChamberSnapshot previous;
        ChamberSnapshot next;
        do {
            previous = state.get();
            next = Objects.requireNonNull(update.apply(previous), "updated state");
        } while (!state.compareAndSet(previous, next));
        return next;
    }
}
