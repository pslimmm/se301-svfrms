package org.example.svfrms.domain.chamber;

/**
 * Immutable point-in-time snapshot of the environmental conditions within a growing chamber.
 */
public record ChamberSnapshot(
        double temperature,
        double humidity,
        double soilMoisture,
        double lightLevel,
        double nutrientLevel
) {
    public static ChamberSnapshot defaultInitialState() {
        return new ChamberSnapshot(24.0, 70.0, 60.0, 600.0, 70.0);
    }
}
