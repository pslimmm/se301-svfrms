package org.example;

import java.util.Objects;

/** Immutable command data; chamber identifiers need no delimiter encoding or escaping. */
public record ControlRequest(String chamberId, DeviceType deviceType) {
    public ControlRequest {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(deviceType, "deviceType");
    }
}
