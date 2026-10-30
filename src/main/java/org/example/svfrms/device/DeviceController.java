package org.example.svfrms.device;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.example.svfrms.chamber.ChamberStateStore;

/**
 * Runs a named device against a chamber and tells listeners what happened. This is the "receiver" that
 * control commands call into. An unknown device name is ignored, just like the legacy
 * {@code activateControl()} ignored unknown strings.
 */
public final class DeviceController {

    private final DeviceRegistry devices;
    private final ChamberStateStore states;
    private final List<DeviceListener> listeners = new CopyOnWriteArrayList<>();

    public DeviceController(DeviceRegistry devices, ChamberStateStore states) {
        this.devices = devices;
        this.states = states;
    }

    public void addListener(DeviceListener listener) {
        listeners.add(listener);
    }

    public void activate(String chamberId, String deviceName) {
        devices.find(deviceName).ifPresent(device -> {
            String result = device.activate(states.stateOf(chamberId));
            DeviceActivation activation = new DeviceActivation(chamberId, device.name(), result);
            for (DeviceListener listener : listeners) {
                listener.onDeviceActivated(activation);
            }
        });
    }
}
