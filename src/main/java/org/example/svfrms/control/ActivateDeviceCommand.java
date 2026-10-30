package org.example.svfrms.control;

import org.example.svfrms.device.DeviceController;

/** Concrete command: "switch on {@code deviceName} in {@code chamberId}". */
public record ActivateDeviceCommand(String chamberId, String deviceName, DeviceController controller)
        implements ControlCommand {

    @Override
    public void execute() {
        controller.activate(chamberId, deviceName);
    }
}
