package org.example.svfrms.sensor;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.example.svfrms.model.SensorReading;

/**
 * Keeps a timestamped line for every reading processed (legacy {@code sensorLogs} / "LOG" event).
 *
 * <p><b>Concurrency fix (C3):</b> the legacy log was an {@code ArrayList} written by four threads at once.
 * A {@link ConcurrentLinkedQueue} is safe for many writers and keeps insertion order.
 */
public final class SensorLog implements ReadingListener {

    private final Clock clock;
    private final Queue<String> entries = new ConcurrentLinkedQueue<>();

    public SensorLog(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void onReading(SensorReading reading) {
        record(reading.crop(), reading.type().label(), reading.value());
    }

    @Override
    public void onUnknownSensor(String chamberId, String crop, String sensorName, double value) {
        record(crop, sensorName, value);
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }

    private void record(String crop, String sensorName, double value) {
        // same format as legacy: new Date() + " : " + "Processing Tomato Temperature = 31.0"
        entries.add(new Date(clock.millis()) + " : Processing " + crop + " " + sensorName + " = " + value);
    }
}
