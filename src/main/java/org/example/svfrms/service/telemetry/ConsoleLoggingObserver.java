package org.example.svfrms.service.telemetry;

import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;

import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.function.Consumer;

/** Formats sensor log entries using the legacy timestamp and message layout. */
public final class ConsoleLoggingObserver implements TelemetryObserver {
    private final Consumer<String> output;
    private final Clock clock;

    public ConsoleLoggingObserver() {
        this(System.out::println, Clock.systemDefaultZone());
    }

    public ConsoleLoggingObserver(Consumer<String> output, Clock clock) {
        this.output = Objects.requireNonNull(output, "output");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void onTelemetry(SensorReading reading) {
        output.accept(new Date(clock.millis()) + " : Processing " + reading.crop() + " "
                + legacySensorName(reading.sensorType()) + " = " + reading.value());
    }

    private static String legacySensorName(SensorType type) {
        return switch (type) {
            case TEMPERATURE -> "Temperature";
            case HUMIDITY -> "Humidity";
            case SOIL_MOISTURE -> "Soil";
            case LIGHT -> "Light";
            case NUTRIENT -> "Nutrient";
        };
    }
}
