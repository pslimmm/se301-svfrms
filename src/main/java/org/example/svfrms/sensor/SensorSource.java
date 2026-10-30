package org.example.svfrms.sensor;

import java.util.List;

import org.example.svfrms.model.SensorReading;

/**
 * Where readings come from. Today it is a random simulator. Real hardware could implement this
 * interface later without any other class changing.
 */
public interface SensorSource {

    /** One round of readings for a chamber (one per sensor). */
    List<SensorReading> nextCycle(String chamberId, String crop);
}
