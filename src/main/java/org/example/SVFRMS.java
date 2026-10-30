package org.example;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SVFRMS {
    public static final Path ARCHIVE_DIR = Paths.get("archive");
    public static final Path REPORT_FILE = ARCHIVE_DIR.resolve("daily_report.txt");
    public static final int CONTROL_QUEUE_CAPACITY = 5;

    private final FarmState farmState;
    private final CropCatalog catalog;
    private final ControlQueue controlQueue = new ControlQueue(CONTROL_QUEUE_CAPACITY);
    public enum RunState { NEW, RUNNING, COMPLETED, FAILED, CANCELLED }
    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.NEW);
    private volatile ExecutorService workers;
    private final Random random;
    private final Consumer<String> output;
    private final Path reportFile;

    public SVFRMS() {
        this(new Random(), System.out::println);
    }

    public SVFRMS(CropCatalog catalog) {
        this(catalog, new Random(), System.out::println, REPORT_FILE);
    }

    SVFRMS(Random random, Consumer<String> output) {
        this(random, output, REPORT_FILE);
    }

    SVFRMS(Random random, Consumer<String> output, Path reportFile) {
        this(CropCatalog.legacyDefaults(), random, output, reportFile);
    }

    SVFRMS(CropCatalog catalog, Random random, Consumer<String> output, Path reportFile) {
        this.catalog = Objects.requireNonNull(catalog);
        this.farmState = new FarmState(catalog);
        this.random = Objects.requireNonNull(random);
        this.output = Objects.requireNonNull(output);
        this.reportFile = Objects.requireNonNull(reportFile);
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
            alertLog(chamber, crop, message);
        } else if (type.equals("CONTROL_LOG")) {
            controlLog(chamber, message);
        }
    }

    private void alertLog(String chamber, String crop, String message) {
        output.accept("[" + chamber + "][" + crop + "][ALERT] " + message);
    }

    private void controlLog(String chamber, String message) {
        output.accept("\t\u001B[32m[" + chamber + "][CONTROL] " + message + "\u001B[0m");
    }

    public void activateControl(String chamber, String control) {
        checkInterrupted();
        List<String> messages = farmState.activateControl(chamber, control);
        messages.forEach(message -> controlLog(chamber, message));
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
        result.alerts().forEach(message -> alertLog(chamber, crop, message));
        result.controls().forEach(request -> addControlRequest(request.chamberId(), request.deviceType()));
    }

    public void generateDailyReport() {
        FarmState.Snapshot snapshot = farmState.dailyReportSnapshot();
        output.accept("===== DAILY REPORT =====");
        output.accept("Generated Alerts: " + snapshot.totalAlertsGenerated());
        output.accept("Active Alerts: " + snapshot.totalAlerts());
        output.accept("Water Usage: " + snapshot.totalWaterUsage() + " L");
        output.accept("Energy Usage: " + snapshot.totalEnergyUsage() + " kWh");
    }

    public void archiveAlerts() {
        int count = farmState.archiveAlerts();
        output.accept(count == 0 ? "Archiving alerts... no active alerts to archive."
            : "Archiving alerts... " + count + " moved to archive.");
    }

    public void irrigationCycle() {
        farmState.addUsage(100, 0);
    }

    public void feedingCycle() {
        farmState.addUsage(0, 20);
    }

    public void showAlerts() {
        snapshot().alerts().forEach(output);
    }

    public void showLogs() {
        snapshot().sensorLogs().forEach(output);
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

    final class ChamberTask implements Runnable {
        private final String chamber;
        private final String crop;

        ChamberTask(String chamber,String crop){
            this.chamber=chamber;
            this.crop=crop;
        }

        public void run(){
            for(int i=0;i<20;i++){
                checkInterrupted();
                processSensor(chamber,crop,"Temperature",10+random.nextInt(30));
                processSensor(chamber,crop,"Humidity",40+random.nextInt(60));
                processSensor(chamber,crop,"Soil",20+random.nextInt(60));
                processSensor(chamber,crop,"Light",300+random.nextInt(900));
                processSensor(chamber,crop,"Nutrient",20+random.nextInt(80));
                farmState.addUsage(2, 1);
            }
        }
    }

    public void saveArchiveReport() {
        FarmState.Snapshot snapshot = snapshot();
        try {
            Files.createDirectories(reportFile.toAbsolutePath().getParent());

            ArrayList<String> reportLines = new ArrayList<String>();
            reportLines.add("===== DAILY REPORT =====");
            reportLines.add("Timestamp: " + new Date());
            reportLines.add("Generated Alerts: " + snapshot.totalAlertsGenerated());
            reportLines.add("Active Alerts: " + snapshot.totalAlerts());
            reportLines.add("Water Usage: " + snapshot.totalWaterUsage() + " L");
            reportLines.add("Energy Usage: " + snapshot.totalEnergyUsage() + " kWh");
            reportLines.add("Archived alerts: " + snapshot.archivedAlerts().size());
            reportLines.add("---");
            reportLines.addAll(snapshot.reports());

            if (snapshot.chamberSummaries().size() > 0) {
                reportLines.add("--- chamber wise summary ---");
                TreeSet<String> sortedChambers = new TreeSet<String>(snapshot.chamberSummaries().keySet());
                for (String chamber : sortedChambers) {
                    FarmState.SummarySnapshot summary = snapshot.chamberSummaries().get(chamber);
                    reportLines.add(chamber + " -> Alerts=" + summary.alerts() + ", Actions=" + summary.mitigationActions() + ", Last Action=" + summary.lastAction() + ", Last Alert=" + summary.lastAlert());
                }
            }

            if (snapshot.archivedAlerts().size() > 0) {
                reportLines.add("--- archived alerts ---");
                reportLines.addAll(snapshot.archivedAlerts());
            }

            Files.write(reportFile, reportLines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            output.accept("Archive report saved to: " + reportFile.toAbsolutePath());
        } catch (Exception e) {
            output.accept("Could not save archive report: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        new SVFRMS().run();
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
                ChamberTask task = new ChamberTask(assignment.chamberId(), assignment.cropName());
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
            output.accept("System finished sensor processing.");
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
