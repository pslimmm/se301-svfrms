package org.example;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** One chamber's finite legacy simulation; lifecycle ownership belongs to the application. */
public final class ChamberSimulation implements Runnable {
    private static final List<SensorType> ORDER = List.of(SensorType.TEMPERATURE, SensorType.HUMIDITY,
        SensorType.SOIL, SensorType.LIGHT, SensorType.NUTRIENT);
    private final String chamber;
    private final String crop;
    private final SensorSource source;
    private final Consumer<SensorReading> readings;
    private final FarmState state;

    public ChamberSimulation(String chamber, String crop, SensorSource source,
                             Consumer<SensorReading> readings, FarmState state) {
        this.chamber = Objects.requireNonNull(chamber);
        this.crop = Objects.requireNonNull(crop);
        this.source = Objects.requireNonNull(source);
        this.readings = Objects.requireNonNull(readings);
        this.state = Objects.requireNonNull(state);
    }

    @Override public void run() {
        for (int i = 0; i < 20; i++) {
            for (SensorType sensor : ORDER) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException("Operation interrupted");
                double value = source.read(chamber, sensor);
                readings.accept(new SensorReading(chamber, crop, sensor, value));
            }
            state.addUsage(2, 1);
        }
    }
}
