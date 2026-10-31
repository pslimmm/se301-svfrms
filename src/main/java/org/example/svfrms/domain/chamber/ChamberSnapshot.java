package org.example.svfrms.domain.chamber;

/** Immutable environmental state captured for one chamber. */
public record ChamberSnapshot(
        double temperature,
        double humidity,
        double soilMoisture,
        double lightLevel,
        double nutrientLevel) {

    public ChamberSnapshot(ChamberSnapshot source) {
        this(source.temperature, source.humidity, source.soilMoisture, source.lightLevel, source.nutrientLevel);
    }
}
