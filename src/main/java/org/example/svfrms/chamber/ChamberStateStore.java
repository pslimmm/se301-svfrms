package org.example.svfrms.chamber;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up (and lazily creates) the state of each chamber.
 *
 * <p><b>Concurrency fix (C1):</b> the legacy {@code getChamberState} did
 * "if (!map.containsKey) map.put" on a plain HashMap. Two threads could both create a state, and
 * one run crashed with a NullPointerException. {@code computeIfAbsent} on a ConcurrentHashMap
 * does the check and the insert as one atomic step.
 */
public final class ChamberStateStore {

    private final Map<String, ChamberState> states = new ConcurrentHashMap<>();

    public ChamberState stateOf(String chamberId) {
        return states.computeIfAbsent(chamberId, id -> new ChamberState());
    }
}
