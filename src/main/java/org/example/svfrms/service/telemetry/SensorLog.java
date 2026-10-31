package org.example.svfrms.service.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns append-heavy retained log entries and returns immutable ordered inspection copies. */
public final class SensorLog {
    private final List<String> entries = new ArrayList<>();

    public synchronized void append(String entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
    }

    public synchronized List<String> snapshot() {
        return List.copyOf(entries);
    }
}
