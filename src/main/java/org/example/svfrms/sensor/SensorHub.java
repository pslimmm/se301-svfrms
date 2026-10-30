package org.example.svfrms.sensor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.example.svfrms.model.SensorReading;
import org.example.svfrms.model.SensorType;

/**
 * Observer subject for sensor readings. Chamber tasks publish here, and the sensor log and the chamber
 * monitor subscribe. The publisher doesn't need to know who is listening, so adding a new consumer
 * (e.g. a dashboard) means subscribing it, with no edits to the publishers.
 */
public final class SensorHub {

    private final List<ReadingListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(ReadingListener listener) {
        listeners.add(listener);
    }

    public void publish(SensorReading reading) {
        for (ReadingListener listener : listeners) {
            listener.onReading(reading);
        }
    }

    /** Entry point for raw input, the same shape as the legacy {@code processSensor(chamber, crop, sensor, value)}. */
    public void publish(String chamberId, String crop, String sensorName, double value) {
        Optional<SensorType> type = SensorType.fromLabel(sensorName);
        if (type.isPresent()) {
            publish(new SensorReading(chamberId, crop, type.get(), value));
        } else {
            for (ReadingListener listener : listeners) {
                listener.onUnknownSensor(chamberId, crop, sensorName, value);
            }
        }
    }
}
