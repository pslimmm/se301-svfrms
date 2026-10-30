package org.example.svfrms.device;

import org.example.svfrms.chamber.ChamberState;

/**
 * An automated device that changes the conditions in a chamber (Actuator).
 *
 * <p>The interface is deliberately tiny (Interface Segregation): a device only needs a name and a way
 * to act. Each device is its own class, so adding one never touches the others (Open/Closed). The
 * legacy code had all of them in one if/else chain in {@code activateControl()}.
 */
public interface Device {

    /** Name used in configuration and output, e.g. "Cooling Fan". */
    String name();

    /**
     * Applies the device's effect to the chamber and returns the result line,
     * e.g. "Temperature reduced to 23.0C".
     */
    String activate(ChamberState state);
}
