package org.example.svfrms.service.resource;

import java.util.concurrent.atomic.LongAdder;

/** Loss-resistant resource accounting; AlertManagementService owns alert counts. */
public final class ResourceAccountingService {
    private static final long WATER_PER_IRRIGATION_CYCLE_LITRES = 100;
    private static final long ENERGY_PER_FEEDING_CYCLE_KWH = 20;

    private final LongAdder waterUsage = new LongAdder();
    private final LongAdder energyUsage = new LongAdder();

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
        recordWater(WATER_PER_IRRIGATION_CYCLE_LITRES);
    }

    /** Preserves the legacy feeding cycle's 20 kWh resource charge. */
    public void feedingCycle() {
        recordEnergy(ENERGY_PER_FEEDING_CYCLE_KWH);
    }

    public long waterUsage() {
        return waterUsage.sum();
    }

    public long energyUsage() {
        return energyUsage.sum();
    }

    private static void requireNonNegative(long value, String resource) {
        if (value < 0) {
            throw new IllegalArgumentException(resource + " usage must be non-negative");
        }
    }
}
