package org.example.svfrms.control;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import org.example.svfrms.device.DeviceController;

/**
 * Producer/consumer queue of device commands, the modern version of the legacy {@code controlQueue}
 * plus {@code ControlThread}.
 *
 * <p><b>Concurrency fixes (C8, C10):</b>
 * <ul>
 *   <li>A bounded {@link ArrayBlockingQueue} (capacity 5 like the legacy queue) replaces the hand-written
 *       {@code wait()/notifyAll()} code.</li>
 *   <li>{@code InterruptedException} is never swallowed. We restore the interrupt flag so the thread can be
 *       cancelled. The legacy {@code catch(Exception e){}} made a blocked producer impossible to stop.</li>
 *   <li>A failing command is reported and the worker keeps going. In the legacy code one exception
 *       killed the consumer, and then producers waited on a full queue forever.</li>
 *   <li>Shutdown uses a "poison pill" that goes to the back of the queue, so every earlier command
 *       still runs. The legacy code also emptied the queue before stopping.</li>
 * </ul>
 */
public final class QueuedControlDispatcher implements ControlDispatcher {

    public static final int LEGACY_CAPACITY = 5;

    private static final ControlCommand STOP = () -> {
    };

    private final DeviceController controller;
    private final BlockingQueue<ControlCommand> queue;

    public QueuedControlDispatcher(DeviceController controller, int capacity) {
        this.controller = controller;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void request(String chamberId, String deviceName) {
        put(new ActivateDeviceCommand(chamberId, deviceName, controller));
    }

    @Override
    public void start(ExecutorService executor) {
        executor.submit(this::consumeUntilStopped);
    }

    @Override
    public void stopAndDrain() {
        put(STOP);
    }

    int pending() {
        return queue.size();
    }

    private void put(ControlCommand command) {
        try {
            queue.put(command); // blocks while the queue is full, like the legacy wait() loop
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // let the caller see it was cancelled
        }
    }

    private void consumeUntilStopped() {
        try {
            while (true) {
                ControlCommand command = queue.take();
                if (command == STOP) {
                    return;
                }
                runSafely(command);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runSafely(ControlCommand command) {
        try {
            command.execute();
        } catch (RuntimeException e) {
            System.err.println("Control command failed: " + e);
        }
    }
}
