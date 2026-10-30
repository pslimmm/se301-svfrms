package org.example.svfrms.control;

import org.example.svfrms.device.DeviceController;

/** Runs each command straight away on the calling thread. Used for deterministic (--sequential) runs and tests. */
public final class ImmediateControlDispatcher implements ControlDispatcher {

    private final DeviceController controller;

    public ImmediateControlDispatcher(DeviceController controller) {
        this.controller = controller;
    }

    @Override
    public void request(String chamberId, String deviceName) {
        new ActivateDeviceCommand(chamberId, deviceName, controller).execute();
    }
}
