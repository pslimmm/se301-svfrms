package org.example.svfrms.domain.chamber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Thread-safe mutable state container for a chamber, backed by AtomicReference compare-and-swap.
 */
public class ChamberStateTracker {

    private final ChamberId chamberId;
    private final AtomicReference<ChamberSnapshot> stateRef;

    public ChamberStateTracker(ChamberId chamberId) {
        this(chamberId, ChamberSnapshot.defaultInitialState());
    }

    public ChamberStateTracker(ChamberId chamberId, ChamberSnapshot initial) {
        this.chamberId = Objects.requireNonNull(chamberId, "chamberId must not be null");
        this.stateRef = new AtomicReference<>(Objects.requireNonNull(initial, "initial snapshot must not be null"));
    }

    public ChamberId getChamberId() {
        return chamberId;
    }

    public ChamberSnapshot getSnapshot() {
        return stateRef.get();
    }

    public ChamberSnapshot update(UnaryOperator<ChamberSnapshot> operator) {
        return stateRef.updateAndGet(operator);
    }
}
