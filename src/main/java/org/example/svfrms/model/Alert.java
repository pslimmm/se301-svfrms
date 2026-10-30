package org.example.svfrms.model;

import java.util.Objects;

/**
 * An immutable alert. {@code message} is the exact text the legacy system produced,
 * e.g. "CH1 Tomato High Temp", so all output formats stay the same.
 */
public record Alert(String chamberId, String crop, SensorType sensor, Breach breach,
                    AlertSeverity severity, String message) {

    public Alert {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(crop, "crop");
        Objects.requireNonNull(sensor, "sensor");
        Objects.requireNonNull(breach, "breach");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }
}
