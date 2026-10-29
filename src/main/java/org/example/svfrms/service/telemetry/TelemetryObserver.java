package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;

@FunctionalInterface
public interface TelemetryObserver {
    void onTelemetry(SensorReading reading);
}
