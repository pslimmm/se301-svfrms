package org.example.svfrms.concurrency;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Bounded queue with timed producer operations to keep overload from hanging callers. */
public final class BoundedControlQueue {
    public static final int DEFAULT_CAPACITY = 1024;
    private final ArrayBlockingQueue<ActuatorRequest> queue;

    public BoundedControlQueue() {
        this(DEFAULT_CAPACITY);
    }

    public BoundedControlQueue(int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(ActuatorRequest request, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        return queue.offer(request, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public ActuatorRequest take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
