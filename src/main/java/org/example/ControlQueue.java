package org.example;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Bounded FIFO with graceful admission closure and abrupt cancellation. */
final class ControlQueue {
    private enum State { OPEN, DRAINING, ABORTED }

    private final ArrayBlockingQueue<ControlRequest> requests;
    private final Object admission = new Object();
    private final Set<Thread> submitters = new HashSet<>();
    private State state = State.OPEN;
    private Throwable failure;

    ControlQueue(int capacity) {
        requests = new ArrayBlockingQueue<>(capacity);
    }

    void ensureAccepting() {
        synchronized (admission) {
            requireOpen();
        }
    }

    private void requireOpen() {
        if (state != State.OPEN) {
            throw new RejectedExecutionException("Control submission is closed");
        }
    }

    void submit(ControlRequest request) throws InterruptedException {
        Objects.requireNonNull(request);
        beginSubmission();
        try {
            submitAdmitted(request);
        } finally {
            endSubmission();
        }
    }

    void beginSubmission() {
        synchronized (admission) {
            requireOpen();
            if (!submitters.add(Thread.currentThread())) throw new IllegalStateException("Nested submission");
        }
    }

    void submitAdmitted(ControlRequest request) throws InterruptedException {
        // Never hold the admission monitor (or farm-state lock) during backpressure.
        requests.put(Objects.requireNonNull(request));
    }

    void endSubmission() {
        synchronized (admission) {
            submitters.remove(Thread.currentThread());
        }
    }

    /** Returns null only once graceful closure and all admitted submissions have drained. */
    ControlRequest take() throws InterruptedException {
        while (true) {
            synchronized (admission) {
                if (state == State.ABORTED) throw new CancellationException("Control queue aborted");
                if (state == State.DRAINING && submitters.isEmpty() && requests.isEmpty()) return null;
            }
            // A bounded poll lets close wake an idle consumer without a sentinel occupying capacity.
            ControlRequest request = requests.poll(100, TimeUnit.MILLISECONDS);
            if (request != null) {
                synchronized (admission) {
                    if (state == State.ABORTED) throw new CancellationException("Control queue aborted");
                }
                return request;
            }
        }
    }

    void close() {
        synchronized (admission) {
            if (state == State.OPEN) state = State.DRAINING;
        }
    }

    void abort(Throwable cause) {
        synchronized (admission) {
            if (failure == null) failure = Objects.requireNonNull(cause);
            state = State.ABORTED;
            // Includes admitted external callers, not just the supervisor's producer tasks.
            submitters.forEach(Thread::interrupt);
        }
    }

    Throwable failure() {
        synchronized (admission) {
            return failure;
        }
    }

    List<ControlRequest> snapshot() {
        return List.copyOf(requests);
    }
}
