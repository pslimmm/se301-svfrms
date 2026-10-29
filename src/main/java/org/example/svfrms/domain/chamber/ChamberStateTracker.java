package org.example.svfrms.domain.chamber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Atomic owner of a chamber's immutable state snapshot. */
public final class ChamberStateTracker {
    private final AtomicReference<ChamberSnapshot> state;

    public ChamberStateTracker(ChamberSnapshot initialState) {
        state = new AtomicReference<>(Objects.requireNonNull(initialState, "initialState"));
    }

    public ChamberSnapshot snapshot() {
        return state.get();
    }

    /** Applies an update with a compare-and-set loop so concurrent field changes are not overwritten. */
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
