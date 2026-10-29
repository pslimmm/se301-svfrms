package org.example.svfrms.service.resource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Loss-resistant accounting for resources and generated alerts. */
public final class ResourceAccountingService {
    private final LongAdder waterUsage = new LongAdder();
    private final LongAdder energyUsage = new LongAdder();
    private final AtomicInteger generatedAlerts = new AtomicInteger();

    public void recordWater(long amount) {
        requireNonNegative(amount, "water");
        waterUsage.add(amount);
    }

    public void recordEnergy(long amount) {
        requireNonNegative(amount, "energy");
        energyUsage.add(amount);
    }

    /** Preserves the legacy manual irrigation cycle's 100 L resource charge. */
    public void irrigationCycle() {
        recordWater(100);
    }

    /** Preserves the legacy feeding cycle's 20 kWh resource charge. */
    public void feedingCycle() {
        recordEnergy(20);
    }

    public void recordAlert() {
        generatedAlerts.incrementAndGet();
    }

    public long waterUsage() {
        return waterUsage.sum();
    }

    public long energyUsage() {
        return energyUsage.sum();
    }

    public int generatedAlerts() {
        return generatedAlerts.get();
    }

    private static void requireNonNegative(long value, String resource) {
        if (value < 0) {
            throw new IllegalArgumentException(resource + " usage must be non-negative");
        }
    }
}
