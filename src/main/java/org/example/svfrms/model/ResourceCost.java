package org.example.svfrms.model;

/** Extra water and energy used when a particular alert happens (legacy: "totalWaterUsage += 20"). */
public record ResourceCost(int waterLitres, int energyKwh) {

    public static final ResourceCost NONE = new ResourceCost(0, 0);

    public ResourceCost {
        if (waterLitres < 0 || energyKwh < 0) {
            throw new IllegalArgumentException("Resource cost cannot be negative");
        }
    }
}
