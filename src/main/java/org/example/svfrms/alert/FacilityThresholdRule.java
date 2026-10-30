package org.example.svfrms.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.svfrms.model.Alert;
import org.example.svfrms.model.AlertSeverity;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.Range;
import org.example.svfrms.model.SensorReading;
import org.example.svfrms.model.SensorType;

/**
 * A facility-wide limit that applies to every chamber whatever the crop, e.g. "CH1 Low Light Level".
 * In the legacy code the light (&lt; 500 lux) and nutrient (&lt; 40 %) checks did not depend on the crop,
 * so they live here instead of in the crop profiles.
 */
public final class FacilityThresholdRule implements AlertRule {

    private final SensorType type;
    private final Range range;

    public FacilityThresholdRule(SensorType type, Range range) {
        this.type = type;
        this.range = range;
    }

    /** Builds one rule per {@code threshold.<sensor>.min/max} entry in facility.properties. */
    public static List<AlertRule> fromConfig(Map<String, String> config) {
        List<AlertRule> rules = new ArrayList<>();
        for (SensorType sensor : SensorType.values()) {
            String min = config.get("threshold." + sensor.configKey() + ".min");
            String max = config.get("threshold." + sensor.configKey() + ".max");
            if (min != null || max != null) {
                rules.add(new FacilityThresholdRule(sensor, new Range(
                        min == null ? Double.NEGATIVE_INFINITY : Double.parseDouble(min),
                        max == null ? Double.POSITIVE_INFINITY : Double.parseDouble(max))));
            }
        }
        return rules;
    }

    @Override
    public Optional<Alert> evaluate(SensorReading reading) {
        if (reading.type() != type || !reading.isValid()) {
            return Optional.empty();
        }
        if (range.isAbove(reading.value())) {
            return Optional.of(alert(reading, Breach.HIGH));
        }
        if (range.isBelow(reading.value())) {
            return Optional.of(alert(reading, Breach.LOW));
        }
        return Optional.empty();
    }

    private static Alert alert(SensorReading reading, Breach breach) {
        String message = reading.chamberId() + " " + breach.word() + " " + reading.type().alertName();
        return new Alert(reading.chamberId(), reading.crop(), reading.type(), breach, AlertSeverity.WARNING, message);
    }
}
