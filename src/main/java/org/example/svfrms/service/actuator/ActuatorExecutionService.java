package org.example.svfrms.service.actuator;

import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.concurrency.ActuatorRequest;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Single consumer for ordered, bounded actuator work. */
public final class ActuatorExecutionService implements AutoCloseable {
    private final BoundedControlQueue queue;
    private final Map<ChamberId, ChamberStateTracker> chamberStates;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final ResourceAccountingService metrics;
    private final Consumer<String> output;
    private volatile Thread worker;
    private Lifecycle lifecycle = Lifecycle.NEW;

    private enum Lifecycle { NEW, RUNNING, CLOSING, CLOSED }

    public ActuatorExecutionService(BoundedControlQueue queue,
                                    Map<ChamberId, ChamberStateTracker> chamberStates,
                                    Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                                    ResourceAccountingService metrics,
                                    Consumer<String> output) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.chamberStates = Map.copyOf(chamberStates);
        this.chamberSummaries = Map.copyOf(chamberSummaries);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.output = Objects.requireNonNull(output, "output");
    }

    public synchronized void start() {
        if (lifecycle != Lifecycle.NEW) {
            throw new IllegalStateException("Actuator service has already started");
        }
        lifecycle = Lifecycle.RUNNING;
        worker = Thread.ofVirtual().name("svfrms-actuator").start(this::consume);
    }

    public synchronized boolean submit(ActuatorRequest request, Duration timeout) throws InterruptedException {
        if (lifecycle != Lifecycle.RUNNING || worker == null || !worker.isAlive()) {
            throw new IllegalStateException("Actuator service is not running");
        }
        return queue.offer(request, timeout);
    }

    public boolean isTerminated() {
        return worker != null && !worker.isAlive();
    }

    private void consume() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActuatorRequest request = queue.take();
                DeviceCommand command = request.command();
                if (command.isPoisonPill()) {
                    return;
                }
                try {
                    ChamberStateTracker state = chamberStates.get(request.chamberId());
                    ChamberSummaryTracker summary = chamberSummaries.get(request.chamberId());
                    if (state == null || summary == null) {
                        throw new IllegalArgumentException("No chamber registered for " + request.chamberId().value());
                    }
                    command.execute(state, metrics);
                    summary.recordMitigationAction(command.actionName());
                    output.accept("\t\u001B[32m[" + request.chamberId().value() + "][CONTROL] " + command.actionName() + "\u001B[0m");
                    output.accept("\t\u001B[32m[" + request.chamberId().value() + "][CONTROL] " + command.stateDescription(state.snapshot()) + "\u001B[0m");
                } catch (RuntimeException failure) {
                    // A malformed command must not kill the only consumer and block producers forever.
                    output.accept("Actuator command failed: " + failure.getMessage());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() throws InterruptedException {
        Thread current = worker;
        if (current == null) {
            lifecycle = Lifecycle.CLOSED;
            return;
        }
        if (lifecycle == Lifecycle.CLOSED && !current.isAlive()) {
            return;
        }
        lifecycle = Lifecycle.CLOSING;
        boolean interrupted = false;
        if (current.isAlive()) {
            long offerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean queued = false;
            while (!queued && current.isAlive()) {
                long remaining = offerDeadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                try {
                    queued = queue.offer(ActuatorRequest.stopSignal(), Duration.ofNanos(remaining));
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (!queued && current.isAlive()) {
                current.interrupt();
            }
            long joinDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (current.isAlive()) {
                long remaining = joinDeadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                try {
                    current.join(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (current.isAlive()) {
                current.interrupt();
                long forcedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (current.isAlive()) {
                    long remaining = forcedDeadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        current.join(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
                if (current.isAlive()) {
                    if (interrupted) Thread.currentThread().interrupt();
                    throw new IllegalStateException("Actuator worker did not terminate");
                }
            }
        }
        lifecycle = Lifecycle.CLOSED;
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Interrupted while closing actuator service");
        }
    }
}
