package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe telemetry publisher that isolates observers from one another's failures. */
public final class TelemetryPublisher {
    private final CopyOnWriteArrayList<TelemetryObserver> observers = new CopyOnWriteArrayList<>();
    private final Consumer<RuntimeException> failureHandler;

    public TelemetryPublisher() {
        this(failure -> System.err.println("Telemetry observer failed: " + failure.getMessage()));
    }

    public TelemetryPublisher(Consumer<RuntimeException> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    public void subscribe(TelemetryObserver observer) {
        observers.addIfAbsent(Objects.requireNonNull(observer, "observer"));
    }

    public void unsubscribe(TelemetryObserver observer) {
        observers.remove(observer);
    }

    public void publish(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        for (TelemetryObserver observer : observers) {
            try {
                observer.onTelemetry(reading);
            } catch (RuntimeException failure) {
                failureHandler.accept(failure);
            }
        }
    }
}
