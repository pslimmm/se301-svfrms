package org.example.svfrms.concurrency;

import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;

import java.time.Instant;
import java.util.Objects;
import java.util.Random;

/**
 * Runnable task simulating telemetry data generation for a single cultivation chamber.
 */
public class ChamberSimulationTask implements Runnable {

    private final ChamberId chamberId;
    private final String crop;
    private final int iterations;
    private final TelemetryPublisher publisher;
    private final ResourceAccountingService metrics;
    private final Random random;

    public ChamberSimulationTask(
            ChamberId chamberId,
            String crop,
            int iterations,
            TelemetryPublisher publisher,
            ResourceAccountingService metrics) {
        this(chamberId, crop, iterations, publisher, metrics, new Random());
    }

    public ChamberSimulationTask(
            ChamberId chamberId,
            String crop,
            int iterations,
            TelemetryPublisher publisher,
            ResourceAccountingService metrics,
            Random random) {
        this.chamberId = Objects.requireNonNull(chamberId, "chamberId must not be null");
        this.crop = Objects.requireNonNull(crop, "crop must not be null");
        this.iterations = iterations;
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.random = random != null ? random : new Random();
    }

    @Override
    public void run() {
        for (int i = 0; i < iterations; i++) {
            publisher.publish(new SensorReading(chamberId, crop, SensorType.TEMPERATURE, 10 + random.nextInt(30), Instant.now()));
            publisher.publish(new SensorReading(chamberId, crop, SensorType.HUMIDITY, 40 + random.nextInt(60), Instant.now()));
            publisher.publish(new SensorReading(chamberId, crop, SensorType.SOIL_MOISTURE, 20 + random.nextInt(60), Instant.now()));
            publisher.publish(new SensorReading(chamberId, crop, SensorType.LIGHT, 300 + random.nextInt(900), Instant.now()));
            publisher.publish(new SensorReading(chamberId, crop, SensorType.NUTRIENT, 20 + random.nextInt(80), Instant.now()));
            metrics.recordWaterUsage(2);
            metrics.recordEnergyUsage(1);
        }
    }

    public ChamberId getChamberId() {
        return chamberId;
    }

    public String getCrop() {
        return crop;
    }
}
