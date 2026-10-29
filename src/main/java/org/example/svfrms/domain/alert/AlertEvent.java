package org.example.svfrms.domain.alert;

import org.example.svfrms.domain.chamber.ChamberId;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain event representing an alert triggered by out-of-boundary telemetry or system events.
 */
public record AlertEvent(
        ChamberId chamberId,
        String crop,
        AlertSeverity severity,
        String message,
        Instant timestamp
) {
    public AlertEvent {
        Objects.requireNonNull(chamberId, "chamberId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
