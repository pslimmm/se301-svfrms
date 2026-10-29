package org.example.svfrms.service.actuator;

import org.example.svfrms.command.DeviceCommand;
import org.example.svfrms.command.PoisonPillCommand;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker service that continuously consumes DeviceCommands from BoundedControlQueue and executes them.
 */
public class ActuatorExecutionService implements Runnable {

    private final BoundedControlQueue queue;
    private final Map<ChamberId, ChamberStateTracker> stateMap;
    private final Map<ChamberId, ChamberSummaryTracker> summaryMap;
    private final ResourceAccountingService metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    public ActuatorExecutionService(
            BoundedControlQueue queue,
            Map<ChamberId, ChamberStateTracker> stateMap,
            Map<ChamberId, ChamberSummaryTracker> summaryMap,
            ResourceAccountingService metrics
    ) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.stateMap = Objects.requireNonNull(stateMap, "stateMap must not be null");
        this.summaryMap = Objects.requireNonNull(summaryMap, "summaryMap must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = Thread.ofVirtual().name("ActuatorExecutionService-Worker").start(this);
        }
    }

    public synchronized void stop() {
        if (running.get()) {
            queue.offer(new PoisonPillCommand());
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (workerThread != null) {
            workerThread.join(unit.toMillis(timeout));
            return !workerThread.isAlive();
        }
        return true;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                DeviceCommand command = queue.take();
                if (command.isPoisonPill()) {
                    running.set(false);
                    break;
                }

                ChamberId chamberId = command.getChamberId();
                ChamberStateTracker stateTracker = stateMap.get(chamberId);
                ChamberSummaryTracker summaryTracker = summaryMap.get(chamberId);

                if (stateTracker != null) {
                    command.execute(stateTracker, summaryTracker, metrics);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
                break;
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
            }
        }
    }
}
