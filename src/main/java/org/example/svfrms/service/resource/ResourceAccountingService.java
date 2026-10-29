package org.example.svfrms.service.resource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe high-throughput resource and alert metrics accumulator backed by LongAdder.
 */
public class ResourceAccountingService {

    private final LongAdder totalWaterUsage = new LongAdder();
    private final LongAdder totalEnergyUsage = new LongAdder();
    private final AtomicInteger activeAlerts = new AtomicInteger(0);
    private final AtomicInteger totalAlertsGenerated = new AtomicInteger(0);

    public void recordWaterUsage(long liters) {
        if (liters > 0) {
            totalWaterUsage.add(liters);
        }
    }

    public void recordEnergyUsage(long kWh) {
        if (kWh > 0) {
            totalEnergyUsage.add(kWh);
        }
    }

    public void recordAlertGenerated() {
        activeAlerts.incrementAndGet();
        totalAlertsGenerated.incrementAndGet();
    }

    public void resetActiveAlerts() {
        activeAlerts.set(0);
    }

    public long getWaterUsageLiters() {
        return totalWaterUsage.sum();
    }

    public long getEnergyUsageKWh() {
        return totalEnergyUsage.sum();
    }

    public int getActiveAlertsCount() {
        return activeAlerts.get();
    }

    public int getTotalAlertsGenerated() {
        return totalAlertsGenerated.get();
    }
}
