package org.example.svfrms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.example.svfrms.concurrency.NamedThreadFactory;
import org.example.svfrms.control.ControlDispatcher;

/**
 * Runs the whole simulation: all chamber tasks, the device worker, and then the end-of-day steps.
 *
 * <p><b>Concurrency fix (C10):</b> the legacy {@code main()} created raw threads and joined them with no
 * timeout or error handling. Here:
 * <ul>
 *   <li>chamber tasks run on a fixed thread pool and the device worker on a single-thread executor;</li>
 *   <li>a failed chamber is reported (the legacy NPE just vanished into a stack trace);</li>
 *   <li>every wait has a timeout, and on timeout or interruption the executors are shut down with
 *       {@code shutdownNow()} so no thread is left hanging.</li>
 * </ul>
 * In sequential mode everything runs on the calling thread, which gives a repeatable output for demos and tests.
 */
public final class FacilitySimulation {

    private static final long TIMEOUT_SECONDS = 60;

    private final List<ChamberTask> chamberTasks;
    private final ControlDispatcher dispatcher;
    private final EndOfDayService endOfDay;
    private final boolean sequential;

    public FacilitySimulation(List<ChamberTask> chamberTasks, ControlDispatcher dispatcher,
                              EndOfDayService endOfDay, boolean sequential) {
        this.chamberTasks = List.copyOf(chamberTasks);
        this.dispatcher = dispatcher;
        this.endOfDay = endOfDay;
        this.sequential = sequential;
    }

    public void run() {
        if (sequential) {
            chamberTasks.forEach(ChamberTask::run);
        } else {
            runConcurrently();
        }
        endOfDay.run();
    }

    private void runConcurrently() {
        ExecutorService control = Executors.newSingleThreadExecutor(new NamedThreadFactory("control"));
        ExecutorService chambers = Executors.newFixedThreadPool(
                Math.max(1, chamberTasks.size()), new NamedThreadFactory("chamber"));
        try {
            dispatcher.start(control);
            List<Future<?>> running = new ArrayList<>();
            for (ChamberTask task : chamberTasks) {
                running.add(chambers.submit(task));
            }
            for (int i = 0; i < running.size(); i++) {
                awaitChamber(chamberTasks.get(i).chamberId(), running.get(i));
            }
            chambers.shutdown();
            dispatcher.stopAndDrain(); // all producers are done, so let the worker finish the queue
            control.shutdown();
            if (!control.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Device worker did not finish in time; stopping it.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            chambers.shutdownNow();
            control.shutdownNow();
        }
    }

    private static void awaitChamber(String chamberId, Future<?> future) throws InterruptedException {
        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            System.err.println("Chamber " + chamberId + " failed: " + e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            System.err.println("Chamber " + chamberId + " timed out and was cancelled.");
        }
    }
}
