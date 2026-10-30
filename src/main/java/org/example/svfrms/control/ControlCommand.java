package org.example.svfrms.control;

/**
 * Command pattern: a request to do something, wrapped in an object so it can be queued and run later
 * on a different thread. The legacy code used the string {@code "CH1|Cooling Fan"} and split it again
 * on the other side.
 */
@FunctionalInterface
public interface ControlCommand {

    void execute();
}
