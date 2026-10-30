package org.example.svfrms.sensor;

import java.util.List;
import java.util.Random;

import org.example.svfrms.model.SensorReading;
import org.example.svfrms.model.SensorType;

/**
 * Produces random readings using the same ranges and the same order of random calls as the legacy
 * {@code ChamberThread}. The {@link Random} is injected so a fixed seed gives a repeatable run.
 * {@code java.util.Random} is thread-safe, so chamber threads can share one instance.
 */
public final class RandomSensorSimulator implements SensorSource {

    private final Random random;

    public RandomSensorSimulator(Random random) {
        this.random = random;
    }

    @Override
    public List<SensorReading> nextCycle(String chamberId, String crop) {
        // the order matters: it must match the legacy calls so a seeded run gives identical values
        double temperature = 10 + random.nextInt(30);
        double humidity = 40 + random.nextInt(60);
        double soil = 20 + random.nextInt(60);
        double light = 300 + random.nextInt(900);
        double nutrient = 20 + random.nextInt(80);
        return List.of(
                new SensorReading(chamberId, crop, SensorType.TEMPERATURE, temperature),
                new SensorReading(chamberId, crop, SensorType.HUMIDITY, humidity),
                new SensorReading(chamberId, crop, SensorType.SOIL_MOISTURE, soil),
                new SensorReading(chamberId, crop, SensorType.LIGHT, light),
                new SensorReading(chamberId, crop, SensorType.NUTRIENT, nutrient));
    }
}
