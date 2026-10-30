package org.example.svfrms.sensor;

import org.example.svfrms.model.SensorReading;

/** Observer: anything that wants to see sensor readings as they arrive. */
public interface ReadingListener {

    void onReading(SensorReading reading);

    /**
     * Called for a sensor name the system does not recognise. The legacy code logged such readings and
     * otherwise ignored them, so by default listeners ignore them too.
     */
    default void onUnknownSensor(String chamberId, String crop, String sensorName, double value) {
    }
}
