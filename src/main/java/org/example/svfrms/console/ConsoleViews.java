package org.example.svfrms.console;

import java.io.PrintStream;

import org.example.svfrms.alert.AlertService;
import org.example.svfrms.model.Alert;
import org.example.svfrms.sensor.SensorLog;

/** The legacy {@code showAlerts()} and {@code showLogs()} console dumps. */
public final class ConsoleViews {

    private final PrintStream out;
    private final AlertService alerts;
    private final SensorLog sensorLog;

    public ConsoleViews(PrintStream out, AlertService alerts, SensorLog sensorLog) {
        this.out = out;
        this.alerts = alerts;
        this.sensorLog = sensorLog;
    }

    public void showAlerts() {
        for (Alert alert : alerts.snapshot().active()) {
            out.println(alert.message());
        }
    }

    public void showLogs() {
        for (String entry : sensorLog.entries()) {
            out.println(entry);
        }
    }
}
