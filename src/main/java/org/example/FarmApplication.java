package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Composes components and supervises a single finite application run. */
public final class FarmApplication {
    public static final Path ARCHIVE_DIR = Paths.get("archive");
    public static final Path REPORT_FILE = ARCHIVE_DIR.resolve("daily_report.txt");
    public static final int CONTROL_QUEUE_CAPACITY = 5;

    private final FarmState farmState;
    private final CropCatalog catalog;
    private final ControlQueue controlQueue = new ControlQueue(CONTROL_QUEUE_CAPACITY);
    public enum RunState { NEW, RUNNING, COMPLETED, FAILED, CANCELLED }
    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.NEW);
    private volatile ExecutorService workers;
    private final SensorSource source;
    private final ConsoleOutput output;
    private final ReportService reports;

    public FarmApplication() {
        this(new Random(), System.out::println);
    }

    public FarmApplication(CropCatalog catalog) {
        this(catalog, new Random(), System.out::println, REPORT_FILE);
    }

    FarmApplication(Random random, Consumer<String> output) {
        this(random, output, REPORT_FILE);
    }

    FarmApplication(Random random, Consumer<String> output, Path reportFile) {
        this(CropCatalog.legacyDefaults(), random, output, reportFile);
    }

    FarmApplication(CropCatalog catalog, Random random, Consumer<String> output, Path reportFile) {
        this(catalog, new RandomSensorSource(random), Clock.systemDefaultZone(), output, reportFile, new FileArchiveWriter());
    }

    FarmApplication(CropCatalog catalog, SensorSource source, Clock clock, Consumer<String> output,
                    Path reportFile, ArchiveWriter writer) {
        this.catalog = Objects.requireNonNull(catalog);
        this.source = Objects.requireNonNull(source);
        farmState = new FarmState(catalog, clock);
        this.output = new ConsoleOutput(output);
        reports = new ReportService(farmState, this.output, reportFile, writer, clock);
    }

    public RunState runState() {
        return runState.get();
    }

    public boolean workersTerminated() {
        ExecutorService current = workers;
        return current == null || current.isTerminated();
    }

    public FarmState.Snapshot snapshot() {
        return farmState.snapshot();
    }

    public FarmState.ChamberSnapshot getChamberState(String chamber) {
        return farmState.chamber(chamber);
    }

    public FarmState.SummarySnapshot getChamberSummary(String chamber) {
        return farmState.summary(chamber);
    }

    public List<ControlRequest> pendingControls() {
        return controlQueue.snapshot();
    }

    /** Closes admission; the consumer drains queued and already-admitted submissions. */
    public void stopControlThread() {
        controlQueue.close();
    }

    public void handleEvent(String type, String chamber, String crop, String message) {
        farmState.handleEvent(type, chamber, message);
        if (type.equals("ALERT_LOG") || type.equals("CREATE_ALERT")) {
            output.alert(chamber, crop, message);
        } else if (type.equals("CONTROL_LOG")) {
            output.control(chamber, message);
        }
    }

    public void activateControl(String chamber, String control) {
        checkInterrupted();
        List<String> messages = farmState.activateControl(chamber, control);
        messages.forEach(message -> output.control(chamber, message));
    }

    public void addControlRequest(String chamber, DeviceType control) {
        ControlRequest request = new ControlRequest(chamber, control);
        try {
            controlQueue.submit(request);
        } catch (InterruptedException e) {
            throw interruptedOperation(e);
        }
    }

    public void processSensor(String chamber, String crop, String sensor, double value) {
        checkInterrupted();
        controlQueue.ensureAccepting();
        FarmState.SensorResult result = farmState.processSensor(chamber, crop, sensor, value);
        result.alerts().forEach(message -> output.alert(chamber, crop, message));
        result.controls().forEach(request -> addControlRequest(request.chamberId(), request.deviceType()));
    }

    public void generateDailyReport() {
        reports.generateDailyReport();
    }

    public void archiveAlerts() {
        reports.archiveAlerts();
    }

    public void irrigationCycle() {
        farmState.addUsage(100, 0);
    }

    public void feedingCycle() {
        farmState.addUsage(0, 20);
    }

    public void showAlerts() {
        output.lines(snapshot().alerts());
    }

    public void showLogs() {
        output.lines(snapshot().sensorLogs());
    }

    final class ControlTask implements Runnable {
        @Override public void run() {
            try {
                ControlRequest request;
                while ((request = controlQueue.take()) != null) {
                    activateControl(request.chamberId(), request.deviceType().displayName());
                }
            } catch (InterruptedException e) {
                controlQueue.abort(e);
                throw interruptedOperation(e);
            } catch (RuntimeException | Error e) {
                controlQueue.abort(e);
                throw e;
            }
        }
    }

    ChamberSimulation chamberSimulation(String chamber, String crop) {
        return new ChamberSimulation(chamber, crop, source,
            reading -> processSensor(reading.chamber(), reading.crop(), reading.sensor().legacyName(), reading.value()),
            farmState);
    }

    public void saveArchiveReport() {
        reports.saveArchiveReport();
    }

    /** One supervised run per instance. Interrupting the caller cancels owned workers. */
    public void run() throws InterruptedException {
        if (!runState.compareAndSet(RunState.NEW, RunState.RUNNING)) {
            throw new IllegalStateException("This application can only run once");
        }
        List<Future<?>> submitted = new ArrayList<>();
        Throwable failure = null;
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Run interrupted");
            controlQueue.ensureAccepting();
            // A callback that ignores cancellation is diagnosed; it must not pin the CLI JVM forever.
            int producerCount = catalog.assignments().size();
            workers = Executors.newFixedThreadPool(Math.max(2, producerCount + 1),
                Thread.ofPlatform().daemon(true).name("svfrms-worker-", 0).factory());
            CompletionService<Work> completions = new ExecutorCompletionService<>(workers);
            submitted.add(submit(completions, new ControlTask(), Work.CONTROL));
            for (CropCatalog.ChamberAssignment assignment : catalog.assignments()) {
                ChamberSimulation task = chamberSimulation(assignment.chamberId(), assignment.cropName());
                submitted.add(submit(completions, task, Work.PRODUCER));
            }
            int producersFinished = 0;
            if (producerCount == 0) controlQueue.close();
            for (int i = 0; i < producerCount + 1; i++) {
                Work work = completed(completions);
                if (work == Work.PRODUCER) {
                    if (++producersFinished == producerCount) controlQueue.close();
                } else if (producersFinished != producerCount) {
                    throw new IllegalStateException("Control consumer exited before all producers finished");
                }
            }

            // Retain both legacy archive/report orderings, but observe failures in either task.
            submitted.add(submit(completions, this::archiveAlerts, Work.REPORT));
            submitted.add(submit(completions, this::generateDailyReport, Work.REPORT));
            completed(completions);
            completed(completions);
            workers.shutdown();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers did not terminate after completion");
            }
            output.line("System finished sensor processing.");
            saveArchiveReport();
            runState.set(RunState.COMPLETED);
        } catch (InterruptedException e) {
            failure = e;
            runState.set(RunState.CANCELLED);
            Thread.currentThread().interrupt();
            throw e;
        } catch (RuntimeException | Error e) {
            failure = e;
            runState.set(RunState.FAILED);
            throw e;
        } finally {
            if (failure != null) {
                controlQueue.abort(failure);
                submitted.forEach(task -> task.cancel(true));
                if (workers != null) {
                    workers.shutdownNow();
                    if (!awaitCancelledWorkers()) {
                        failure.addSuppressed(new IllegalStateException(
                            "Workers did not terminate within 5 seconds after cancellation"));
                    }
                }
            }
        }
    }

    private enum Work { PRODUCER, CONTROL, REPORT }

    private Future<Work> submit(CompletionService<Work> completions, Runnable task, Work kind) {
        return completions.submit(() -> {
            try {
                task.run();
                checkInterrupted();
                return kind;
            } catch (RuntimeException | Error e) {
                controlQueue.abort(e);
                throw e;
            }
        });
    }

    private Work completed(CompletionService<Work> completions) throws InterruptedException {
        try {
            return completions.take().get();
        } catch (ExecutionException e) {
            Throwable original = controlQueue.failure();
            throw new IllegalStateException("Application worker failed", original == null ? e.getCause() : original);
        }
    }

    private boolean awaitCancelledWorkers() {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (!workers.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    if (workers.awaitTermination(remaining, TimeUnit.NANOSECONDS)) return true;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Operation interrupted");
    }

    private static CancellationException interruptedOperation(InterruptedException cause) {
        Thread.currentThread().interrupt();
        CancellationException cancelled = new CancellationException("Operation interrupted");
        cancelled.initCause(cause);
        return cancelled;
    }
}
