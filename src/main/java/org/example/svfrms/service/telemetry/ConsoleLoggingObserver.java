package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer capturing telemetry readings into memory logs matching legacy output.
 */
public class ConsoleLoggingObserver implements TelemetryObserver {

    private final List<String> sensorLogs;

    public ConsoleLoggingObserver() {
        this(new CopyOnWriteArrayList<>());
    }

    public ConsoleLoggingObserver(List<String> sensorLogs) {
        this.sensorLogs = sensorLogs;
    }

    @Override
    public void onTelemetry(SensorReading reading) {
        String logEntry = new Date() + " : Processing " + reading.crop() + " " + reading.sensorType().legacyKey() + " = " + reading.value();
        sensorLogs.add(logEntry);
    }

    public List<String> getSensorLogs() {
        return sensorLogs;
    }
}
