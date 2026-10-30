package org.example.svfrms.device;

import org.example.svfrms.alert.AlertListener;
import org.example.svfrms.control.ControlDispatcher;
import org.example.svfrms.model.Alert;

/**
 * Listens for alerts and asks for the matching device, e.g. High Temp means Cooling Fan. It only
 * <em>requests</em> the device. The {@link ControlDispatcher} decides whether it runs now or on the
 * control thread.
 */
public final class DeviceResponder implements AlertListener {

    private final ResponsePlan plan;
    private final ControlDispatcher dispatcher;

    public DeviceResponder(ResponsePlan plan, ControlDispatcher dispatcher) {
        this.plan = plan;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onAlert(Alert alert) {
        plan.deviceFor(alert.sensor(), alert.breach())
                .ifPresent(device -> dispatcher.request(alert.chamberId(), device));
    }
}
