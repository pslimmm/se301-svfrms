package org.example;

import java.util.Objects;
import java.util.Random;

/** The original integer sampling ranges, exposed through the sample-source boundary. */
public final class RandomSensorSource implements SensorSource {
    private final Random random;

    public RandomSensorSource(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override public double read(String chamber, SensorType sensor) {
        return switch (sensor) {
            case TEMPERATURE -> 10 + random.nextInt(30);
            case HUMIDITY -> 40 + random.nextInt(60);
            case SOIL -> 20 + random.nextInt(60);
            case LIGHT -> 300 + random.nextInt(900);
            case NUTRIENT -> 20 + random.nextInt(80);
        };
    }
}
