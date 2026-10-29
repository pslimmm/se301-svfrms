package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;

/**
 * Observer interface for decoupled telemetry processing (GoF Observer Pattern).
 */
@FunctionalInterface
public interface TelemetryObserver {

    /**
     * Notification callback triggered when a new sensor telemetry reading arrives.
     *
     * @param reading the immutable sensor reading event
     */
    void onTelemetry(SensorReading reading);
}
