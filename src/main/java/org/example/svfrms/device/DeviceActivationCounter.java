package org.example.svfrms.device;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Counts how often each device ran. Used by the water, energy and nutrient reports. */
public final class DeviceActivationCounter implements DeviceListener {

    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

    @Override
    public void onDeviceActivated(DeviceActivation activation) {
        counts.computeIfAbsent(activation.deviceName(), name -> new LongAdder()).increment();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> result = new TreeMap<>();
        counts.forEach((name, count) -> result.put(name, count.sum()));
        return result;
    }
}
