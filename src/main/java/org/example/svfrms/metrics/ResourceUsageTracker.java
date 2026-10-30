package org.example.svfrms.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.example.svfrms.model.ResourceCost;

/**
 * Facility-wide water and energy totals.
 *
 * <p><b>Concurrency fix (C2):</b> the legacy {@code totalWaterUsage += 2} from four threads lost updates,
 * because {@code +=} is read, add, write as three separate steps. {@link AtomicLong#addAndGet} does it as
 * one atomic step without a lock.
 */
public final class ResourceUsageTracker {

    private final AtomicLong waterLitres = new AtomicLong();
    private final AtomicLong energyKwh = new AtomicLong();

    public void add(ResourceCost cost) {
        addWater(cost.waterLitres());
        addEnergy(cost.energyKwh());
    }

    public void addWater(long litres) {
        waterLitres.addAndGet(litres);
    }

    public void addEnergy(long kwh) {
        energyKwh.addAndGet(kwh);
    }

    /** Baseline use for one sensing cycle of one chamber (legacy ChamberThread: +2 L, +1 kWh). */
    public void recordChamberCycle() {
        addWater(2);
        addEnergy(1);
    }

    /** Legacy {@code irrigationCycle()}: a full irrigation run uses 100 L. */
    public void irrigationCycle() {
        addWater(100);
    }

    /** Legacy {@code feedingCycle()}: a feeding run uses 20 kWh. */
    public void feedingCycle() {
        addEnergy(20);
    }

    public long water() {
        return waterLitres.get();
    }

    public long energy() {
        return energyKwh.get();
    }
}
