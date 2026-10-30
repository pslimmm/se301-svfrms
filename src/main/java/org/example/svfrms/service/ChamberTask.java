package org.example.svfrms.service;

import org.example.svfrms.metrics.ResourceUsageTracker;
import org.example.svfrms.model.SensorReading;
import org.example.svfrms.sensor.SensorHub;
import org.example.svfrms.sensor.SensorSource;

/**
 * The work of one chamber: read all sensors {@code cycles} times and publish the readings.
 *
 * <p>The legacy {@code ChamberThread extends Thread} mixed "what to do" with "how to run it" (LSP/SRP
 * issue). This is a plain {@link Runnable}, so an ExecutorService decides how it runs, and tests can
 * just call {@link #run()}. It stops early if its thread is interrupted.
 */
public final class ChamberTask implements Runnable {

    private final String chamberId;
    private final String crop;
    private final SensorSource source;
    private final SensorHub hub;
    private final ResourceUsageTracker usage;
    private final int cycles;

    public ChamberTask(String chamberId, String crop, SensorSource source, SensorHub hub,
                       ResourceUsageTracker usage, int cycles) {
        this.chamberId = chamberId;
        this.crop = crop;
        this.source = source;
        this.hub = hub;
        this.usage = usage;
        this.cycles = cycles;
    }

    @Override
    public void run() {
        for (int i = 0; i < cycles && !Thread.currentThread().isInterrupted(); i++) {
            for (SensorReading reading : source.nextCycle(chamberId, crop)) {
                hub.publish(reading);
            }
            usage.recordChamberCycle();
        }
    }

    public String chamberId() {
        return chamberId;
    }
}
