package org.example.svfrms.device;

/** Event published after a device ran: which chamber, which device, and the result line. */
public record DeviceActivation(String chamberId, String deviceName, String result) {
}
