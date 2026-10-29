package org.example.svfrms.concurrency;

import org.example.svfrms.command.DeviceCommand;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe bounded FIFO queue for actuator control commands.
 */
public class BoundedControlQueue {

    private final BlockingQueue<DeviceCommand> queue;

    public BoundedControlQueue() {
        this(1024);
    }

    public BoundedControlQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity, true);
    }

    public void put(DeviceCommand command) throws InterruptedException {
        queue.put(Objects.requireNonNull(command, "command must not be null"));
    }

    public boolean offer(DeviceCommand command) {
        return queue.offer(Objects.requireNonNull(command, "command must not be null"));
    }

    public boolean offer(DeviceCommand command, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(Objects.requireNonNull(command, "command must not be null"), timeout, unit);
    }

    public DeviceCommand take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
