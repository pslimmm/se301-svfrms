package org.example.svfrms.console;

import java.io.PrintStream;

import org.example.svfrms.alert.AlertListener;
import org.example.svfrms.device.DeviceActivation;
import org.example.svfrms.device.DeviceListener;
import org.example.svfrms.model.Alert;

/**
 * Prints alert and device lines in exactly the legacy format, including the green ANSI colour for
 * control lines. All console formatting lives here, so the domain classes never print anything (SRP).
 */
public final class ConsoleEventPrinter implements AlertListener, DeviceListener {

    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    private final PrintStream out;

    public ConsoleEventPrinter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onAlert(Alert alert) {
        out.println("[" + alert.chamberId() + "][" + alert.crop() + "][ALERT] " + alert.message());
    }

    @Override
    public void onDeviceActivated(DeviceActivation activation) {
        // lock the stream so the two lines of one activation are never split by another thread (C12)
        synchronized (out) {
            out.println(controlLine(activation.chamberId(), activation.deviceName() + " Activated"));
            out.println(controlLine(activation.chamberId(), activation.result()));
        }
    }

    private static String controlLine(String chamberId, String message) {
        return "\t" + GREEN + "[" + chamberId + "][CONTROL] " + message + RESET;
    }
}
