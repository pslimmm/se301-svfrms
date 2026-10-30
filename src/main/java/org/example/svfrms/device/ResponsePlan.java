package org.example.svfrms.device;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.example.svfrms.model.Breach;
import org.example.svfrms.model.SensorType;

/**
 * Which device to switch on for which kind of alert, read from facility.properties:
 *
 * <pre>
 * response.temperature.high=Cooling Fan
 * response.soil.low=Water Pump
 * </pre>
 *
 * In the legacy code these pairs were buried inside each crop's if-block.
 */
public final class ResponsePlan {

    private final Map<String, String> deviceByAlert;

    private ResponsePlan(Map<String, String> deviceByAlert) {
        this.deviceByAlert = Map.copyOf(deviceByAlert);
    }

    public static ResponsePlan fromConfig(Map<String, String> config) {
        Map<String, String> plan = new HashMap<>();
        for (SensorType sensor : SensorType.values()) {
            for (Breach breach : Breach.values()) {
                String device = config.get("response." + key(sensor, breach));
                if (device != null) {
                    plan.put(key(sensor, breach), device);
                }
            }
        }
        return new ResponsePlan(plan);
    }

    public Optional<String> deviceFor(SensorType sensor, Breach breach) {
        return Optional.ofNullable(deviceByAlert.get(key(sensor, breach)));
    }

    private static String key(SensorType sensor, Breach breach) {
        return sensor.configKey() + "." + breach.configKey();
    }
}
