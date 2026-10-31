package org.example.svfrms.concurrency;

import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.sensor.*;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.random.RandomGenerator;

/** Emits the same five sensor streams and per-cycle resource use as the legacy chamber thread. */
public final class ChamberSimulationTask implements Runnable {
    private static final int MIN_TEMPERATURE_CELSIUS = 10;
    private static final int TEMPERATURE_RANGE_SIZE = 30;
    private static final int MIN_HUMIDITY_PERCENT = 40;
    private static final int HUMIDITY_RANGE_SIZE = 60;
    private static final int MIN_SOIL_MOISTURE_PERCENT = 20;
    private static final int SOIL_MOISTURE_RANGE_SIZE = 60;
    private static final int MIN_LIGHT_LUX = 300;
    private static final int LIGHT_RANGE_SIZE = 900;
    private static final int MIN_NUTRIENT_LEVEL_PERCENT = 20;
    private static final int NUTRIENT_LEVEL_RANGE_SIZE = 80;
    private static final long WATER_PER_ITERATION_LITRES = 2;
    private static final long ENERGY_PER_ITERATION_KWH = 1;

    private final ChamberId chamberId;
    private final String crop;
    private final int iterations;
    private final TelemetryPublisher telemetry;
    private final ResourceAccountingService resources;
    private final RandomGenerator random;
    private final Clock clock;

    public ChamberSimulationTask(ChamberId chamberId, String crop, int iterations,
                                 TelemetryPublisher telemetry, ResourceAccountingService resources,
                                 RandomGenerator random, Clock clock) {
        this.chamberId = Objects.requireNonNull(chamberId, "chamberId");
        this.crop = Objects.requireNonNull(crop, "crop");
        if (iterations < 0) throw new IllegalArgumentException("iterations must be non-negative");
        this.iterations = iterations;
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void run() {
        requireActive();
        for (int i = 0; i < iterations; i++) {
            publish(SensorType.TEMPERATURE, MIN_TEMPERATURE_CELSIUS + random.nextInt(TEMPERATURE_RANGE_SIZE));
            publish(SensorType.HUMIDITY, MIN_HUMIDITY_PERCENT + random.nextInt(HUMIDITY_RANGE_SIZE));
            publish(SensorType.SOIL_MOISTURE, MIN_SOIL_MOISTURE_PERCENT + random.nextInt(SOIL_MOISTURE_RANGE_SIZE));
            publish(SensorType.LIGHT, MIN_LIGHT_LUX + random.nextInt(LIGHT_RANGE_SIZE));
            publish(SensorType.NUTRIENT, MIN_NUTRIENT_LEVEL_PERCENT + random.nextInt(NUTRIENT_LEVEL_RANGE_SIZE));
            resources.recordWater(WATER_PER_ITERATION_LITRES);
            resources.recordEnergy(ENERGY_PER_ITERATION_KWH);
        }
    }

    private void publish(SensorType type, double value) {
        requireActive();
        telemetry.publish(new SensorReading(chamberId, crop, type, value, clock.instant()));
        requireActive();
    }

    private static void requireActive() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Chamber simulation interrupted");
        }
    }
}
