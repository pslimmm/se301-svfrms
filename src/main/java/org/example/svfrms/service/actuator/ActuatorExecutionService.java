package org.example.svfrms.service.actuator;

import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.concurrency.ActuatorRequest;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Single ordered consumer. Admission, completion counts and lifecycle share one short-held lock. */
public final class ActuatorExecutionService implements AutoCloseable {
    private final BoundedControlQueue queue;
    private final Map<ChamberId, ChamberStateTracker> chamberStates;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final Consumer<String> output;
    private final Object gate = new Object();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile Thread worker;
    private Lifecycle lifecycle = Lifecycle.NEW;
    private boolean closeOwner;
    private boolean closeFinished;
    private RuntimeException closeFailure;
    private long closeDeadline;
    private long admitted;
    private long completed;
    private enum Lifecycle { NEW, RUNNING, CLOSING, CLOSED }

    public ActuatorExecutionService(BoundedControlQueue queue,
                                    Map<ChamberId, ChamberStateTracker> chamberStates,
                                    Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                    ResourceAccountingService metrics, Consumer<String> output) {
        this(queue, chamberStates, chamberSummaries, output);
        Objects.requireNonNull(metrics, "metrics");
    }

    public ActuatorExecutionService(BoundedControlQueue queue,
                                    Map<ChamberId, ChamberStateTracker> chamberStates,
                                    Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                    Consumer<String> output) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.chamberStates = Objects.requireNonNull(chamberStates, "chamberStates");
        this.chamberSummaries = Objects.requireNonNull(chamberSummaries, "chamberSummaries");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void start() {
        synchronized (gate) {
            if (lifecycle != Lifecycle.NEW) throw new IllegalStateException("Actuator service has already started");
            lifecycle = Lifecycle.RUNNING;
            worker = Thread.ofVirtual().name("svfrms-actuator").start(this::consume);
        }
    }

    public boolean submit(ActuatorRequest request, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(request, "request");
        if (request.command().isPoisonPill()) throw new IllegalArgumentException("Stop signal is reserved for close");
        long deadline = deadline(timeout);
        synchronized (gate) {
            while (true) {
                if (lifecycle != Lifecycle.RUNNING || failure.get() != null || !worker.isAlive())
                    throw new IllegalStateException("Actuator service is not accepting work", failure.get());
                // timedWait releases this lock, so the consumer can always free queue space.
                if (queue.offer(request, Duration.ZERO)) { admitted++; return true; }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(gate, remaining);
            }
        }
    }

    /** Includes execution and summary/output effects; failures remain observable after draining. */
    public void awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = deadline(timeout);
        synchronized (gate) {
            while (completed < admitted) {
                throwIfFailed();
                if (worker == null || !worker.isAlive()) throw new IllegalStateException("Actuator stopped with unfinished work", failure.get());
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException("Timed out waiting for actuator work");
                TimeUnit.NANOSECONDS.timedWait(gate, remaining);
            }
            throwIfFailed();
        }
    }

    public Optional<Throwable> failure() { return Optional.ofNullable(failure.get()); }

    /** Direct legacy controls remain callable after the queued worker has closed. */
    public void executeDirect(ChamberId chamber, DeviceCommand command) {
        ChamberStateTracker state = chamberStates.get(chamber);
        if (state == null) state = chamberStates.computeIfAbsent(chamber, ignored -> ChamberStateTracker.initial());
        ChamberSummaryTracker summary = chamberSummaries.get(chamber);
        if (summary == null) summary = chamberSummaries.computeIfAbsent(chamber, ignored -> new ChamberSummaryTracker());
        ChamberSnapshot committed = command.execute(state);
        summary.recordMitigationAction(command.actionName());
        output.accept("\t\u001B[32m[" + chamber.value() + "][CONTROL] " + command.actionName() + "\u001B[0m");
        output.accept("\t\u001B[32m[" + chamber.value() + "][CONTROL] " + command.stateDescription(committed) + "\u001B[0m");
    }

    public boolean isTerminated() {
        synchronized (gate) { return worker == null ? closeFinished : !worker.isAlive(); }
    }

    private void consume() {
        try {
            while (true) {
                ActuatorRequest request = queue.take();
                synchronized (gate) { gate.notifyAll(); }
                if (request.command().isPoisonPill()) return;
                try {
                    executeDirect(request.chamberId(), request.command());
                } catch (RuntimeException commandFailure) {
                    failure.compareAndSet(null, commandFailure);
                    try { output.accept("Actuator command failed: " + commandFailure.getMessage()); }
                    catch (RuntimeException outputFailure) { if (outputFailure != commandFailure) commandFailure.addSuppressed(outputFailure); }
                } finally {
                    synchronized (gate) { completed++; gate.notifyAll(); }
                }
            }
        } catch (InterruptedException interrupted) {
            failure.compareAndSet(null, interrupted);
            Thread.currentThread().interrupt();
        } catch (Throwable fatal) {
            failure.compareAndSet(null, fatal);
        } finally {
            synchronized (gate) { gate.notifyAll(); }
        }
    }

    @Override public void close() throws InterruptedException { close(Duration.ofSeconds(10)); }

    /** One total budget covers stop insertion and joining; concurrent callers share the outcome. */
    public void close(Duration budget) throws InterruptedException {
        long requestedDeadline = deadline(budget);
        boolean interrupted = Thread.interrupted();
        RuntimeException outcome = null;
        boolean owner = false;
        synchronized (gate) {
            if (!closeOwner) {
                closeOwner = true;
                owner = true;
                closeDeadline = requestedDeadline;
                lifecycle = Lifecycle.CLOSING;
                gate.notifyAll();
            }
        }
        if (owner) {
            try {
                Thread current = worker;
                if (current != null && current.isAlive()) {
                    boolean stopped = false;
                    while (!stopped && current.isAlive()) {
                        long remaining = closeDeadline - System.nanoTime();
                        if (remaining <= 0) break;
                        try { stopped = queue.offer(ActuatorRequest.stopSignal(), Duration.ofNanos(remaining)); }
                        catch (InterruptedException ignored) { interrupted = true; }
                    }
                    while (current.isAlive()) {
                        long remaining = closeDeadline - System.nanoTime();
                        if (remaining <= 0) break;
                        try { TimeUnit.NANOSECONDS.timedJoin(current, remaining); }
                        catch (InterruptedException ignored) { interrupted = true; }
                    }
                    if (current.isAlive()) {
                        current.interrupt();
                        throw new IllegalStateException("Actuator worker did not terminate within shutdown budget; accepted work may be incomplete");
                    }
                }
                synchronized (gate) {
                    if (completed != admitted) throw new IllegalStateException("Actuator stopped with unfinished accepted work", failure.get());
                }
                throwIfFailed();
            } catch (RuntimeException failed) {
                failure.compareAndSet(null, failed);
                outcome = failed;
            } finally {
                synchronized (gate) {
                    closeFailure = outcome;
                    closeFinished = true;
                    lifecycle = Lifecycle.CLOSED;
                    gate.notifyAll();
                }
            }
        } else {
            synchronized (gate) {
                while (!closeFinished) {
                    long remaining = Math.min(closeDeadline, requestedDeadline) - System.nanoTime();
                    if (remaining <= 0) { outcome = new IllegalStateException("Timed out waiting for actuator shutdown"); break; }
                    try { TimeUnit.NANOSECONDS.timedWait(gate, remaining); }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
                if (closeFinished) outcome = closeFailure;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            var exception = new InterruptedException("Interrupted while closing actuator service");
            if (outcome != null) exception.addSuppressed(outcome);
            throw exception;
        }
        if (outcome != null) throw outcome;
    }

    private void throwIfFailed() {
        Throwable recorded = failure.get();
        if (recorded != null) throw new IllegalStateException("Actuator execution failed", recorded);
    }

    private static long deadline(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
        return System.nanoTime() + duration.toNanos();
    }
}
