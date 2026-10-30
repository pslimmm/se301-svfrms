package org.example.svfrms.service;

import java.util.List;

import org.example.svfrms.alert.AlertRule;
import org.example.svfrms.alert.AlertService;
import org.example.svfrms.chamber.ChamberStateStore;
import org.example.svfrms.model.SensorReading;
import org.example.svfrms.sensor.ReadingListener;

/**
 * Replacement for the legacy 65-line {@code processSensor()}. It does just two things: record the reading
 * in the chamber state, and run every {@link AlertRule} over it. What happens after an alert (printing,
 * summaries, water/energy, devices) is handled by the alert listeners, so this class stays small and
 * never changes when crops, devices or outputs change.
 */
public final class ChamberMonitor implements ReadingListener {

    private final ChamberStateStore states;
    private final List<AlertRule> rules;
    private final AlertService alerts;

    public ChamberMonitor(ChamberStateStore states, List<AlertRule> rules, AlertService alerts) {
        this.states = states;
        this.rules = List.copyOf(rules);
        this.alerts = alerts;
    }

    @Override
    public void onReading(SensorReading reading) {
        if (reading.isValid()) {
            states.stateOf(reading.chamberId()).set(reading.type(), reading.value());
        }
        for (AlertRule rule : rules) {
            rule.evaluate(reading).ifPresent(alerts::raise);
        }
    }
}
