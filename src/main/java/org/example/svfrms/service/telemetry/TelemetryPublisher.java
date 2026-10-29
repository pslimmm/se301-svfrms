package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe Subject (GoF Observer Pattern) publishing telemetry readings to registered observers.
 */
public class TelemetryPublisher {

    private final List<TelemetryObserver> observers = new CopyOnWriteArrayList<>();

    public void registerObserver(TelemetryObserver observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        observers.add(observer);
    }

    public void unregisterObserver(TelemetryObserver observer) {
        observers.remove(observer);
    }

    public void publish(SensorReading reading) {
        Objects.requireNonNull(reading, "reading must not be null");
        for (TelemetryObserver observer : observers) {
            try {
                observer.onTelemetry(reading);
            } catch (Exception e) {
                // Safeguard publisher loop from observer exceptions
                System.err.println("Error notifying telemetry observer: " + e.getMessage());
            }
        }
    }

    public int getObserverCount() {
        return observers.size();
    }
}
