package org.example.svfrms.control;

import java.util.concurrent.ExecutorService;

/**
 * Decides <em>when and where</em> a device request runs. Code that asks for a device depends only on
 * this interface (Dependency Inversion), so we can swap the threaded queue for an immediate version in
 * tests and deterministic runs without changing the callers.
 */
public interface ControlDispatcher {

    void request(String chamberId, String deviceName);

    /** Start any background worker. Does nothing for dispatchers that run commands immediately. */
    default void start(ExecutorService executor) {
    }

    /** Finish all pending requests and stop the worker. Does nothing for immediate dispatchers. */
    default void stopAndDrain() {
    }
}
