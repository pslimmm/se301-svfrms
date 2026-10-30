package org.example.svfrms.device;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of available devices, looked up by name ("Cooling Fan", "Water Pump", ...). */
public final class DeviceRegistry {

    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    public void register(Device device) {
        devices.put(device.name(), device);
    }

    public Optional<Device> find(String name) {
        return Optional.ofNullable(devices.get(name));
    }

    public List<String> names() {
        return devices.keySet().stream().sorted().toList();
    }
}
