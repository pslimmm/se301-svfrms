package org.example.svfrms.domain.crop;

/** Resource charges applied when a crop's out-of-range condition is detected, before control execution. */
public record MitigationResourceCosts(long highTemperatureEnergy, long lowTemperatureEnergy,
                                      long lowHumidityWater, long lowSoilMoistureWater) {
    public MitigationResourceCosts {
        if (highTemperatureEnergy < 0 || lowTemperatureEnergy < 0
                || lowHumidityWater < 0 || lowSoilMoistureWater < 0) {
            throw new IllegalArgumentException("Mitigation resource costs must be non-negative");
        }
    }

    public static MitigationResourceCosts none() {
        return new MitigationResourceCosts(0, 0, 0, 0);
    }
}
