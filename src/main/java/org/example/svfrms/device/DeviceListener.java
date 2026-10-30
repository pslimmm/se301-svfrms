package org.example.svfrms.device;

/** Observer: anything that wants to know when a device has been activated. */
@FunctionalInterface
public interface DeviceListener {

    void onDeviceActivated(DeviceActivation activation);
}
