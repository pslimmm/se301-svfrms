package org.example.svfrms.concurrency;

import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.random.RandomGenerator;

/** Emits the same five sensor streams and per-cycle resource use as the legacy chamber thread. */
public final class ChamberSimulationTask implements Runnable {
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
            publish(SensorType.TEMPERATURE, 10 + random.nextInt(30));
            publish(SensorType.HUMIDITY, 40 + random.nextInt(60));
            publish(SensorType.SOIL_MOISTURE, 20 + random.nextInt(60));
            publish(SensorType.LIGHT, 300 + random.nextInt(900));
            publish(SensorType.NUTRIENT, 20 + random.nextInt(80));
            resources.recordWater(2);
            resources.recordEnergy(1);
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
