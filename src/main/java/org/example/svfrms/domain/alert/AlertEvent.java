package org.example.svfrms.domain.alert;

import org.example.svfrms.domain.chamber.ChamberId;

import java.time.Instant;
import java.util.Objects;

/** Immutable alert produced by threshold evaluation. */
public record AlertEvent(ChamberId chamberId, String crop, AlertSeverity severity, String message, Instant timestamp) {
    public AlertEvent {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(crop, "crop");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
        if (crop.isBlank()) {
            throw new IllegalArgumentException("Crop name must not be blank");
        }
    }

    public AlertEvent(AlertEvent source) {
        this(source.chamberId, source.crop, source.severity, source.message, source.timestamp);
    }
}
