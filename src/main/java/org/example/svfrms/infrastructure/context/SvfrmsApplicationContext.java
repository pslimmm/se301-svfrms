package org.example.svfrms.infrastructure.context;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.concurrency.ChamberSimulationTask;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.command.*;
import org.example.svfrms.concurrency.ActuatorRequest;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.report.DailyReportService;
import org.example.svfrms.service.report.PlainTextReportFormatter;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.ConsoleLoggingObserver;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.example.svfrms.service.telemetry.ThresholdEvaluationObserver;
import org.example.svfrms.domain.sensor.SensorReading;

import org.example.svfrms.infrastructure.storage.FileReportStorageAdapter;

import java.nio.file.Path;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.example.svfrms.service.telemetry.SensorLog;
import org.example.svfrms.domain.actuator.ActuatorType;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/** Pure constructor-injected composition root for the complete simulation. */
public final class SvfrmsApplicationContext implements AutoCloseable {
    private static final List<ChamberSetup> STANDARD_CHAMBERS = List.of(
            new ChamberSetup("CH1", "Tomato"),
            new ChamberSetup("CH2", "Strawberry"),
            new ChamberSetup("CH3", "Lettuce"),
            new ChamberSetup("CH4", "Chili"));

    private final Path reportPath;
    private final Clock clock;
    private final Consumer<String> output;
    private final long randomSeed;
    private final CropRegistry cropRegistry;
    private final ResourceAccountingService resources;
    private final AlertManagementService alerts;
    private final Map<ChamberId, ChamberStateTracker> chamberStates;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final SensorLog sensorLogs = new SensorLog();
    private final TelemetryPublisher telemetry;
    private final ActuatorExecutionService actuatorService;
    private final DailyReportService reports;
    private final AtomicBoolean simulationStarted = new AtomicBoolean();
    private final Duration shutdownBudget;
    private final Object lifecycle = new Object();
    private volatile boolean closing;
    private boolean cleanupFinished;
    private RuntimeException cleanupFailure;
    private long cleanupDeadline;
    private boolean actuatorsStarted;
    private volatile ExecutorService simulationExecutor;

    public SvfrmsApplicationContext() {
        this(Path.of("archive", "daily_report.txt"), Clock.systemDefaultZone(), System.out::println, 42L);
    }

    public SvfrmsApplicationContext(Path reportPath, Clock clock, Consumer<String> output, long randomSeed) {
        this(reportPath, clock, output, randomSeed, Duration.ofSeconds(10));
    }

    public SvfrmsApplicationContext(Path reportPath, Clock clock, Consumer<String> output, long randomSeed,
                                    Duration shutdownBudget) {
        this.reportPath = Objects.requireNonNull(reportPath, "reportPath");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.output = Objects.requireNonNull(output, "output");
        this.randomSeed = randomSeed;
        this.shutdownBudget = Objects.requireNonNull(shutdownBudget, "shutdownBudget");
        if (shutdownBudget.isNegative() || shutdownBudget.isZero()) throw new IllegalArgumentException("shutdownBudget must be positive");
        cropRegistry = CropRegistry.withStandardProfiles();
        resources = new ResourceAccountingService();
        alerts = new AlertManagementService();

        chamberStates = new ConcurrentHashMap<>();
        chamberSummaries = new ConcurrentHashMap<>();

        BoundedControlQueue controlQueue = new BoundedControlQueue();
        actuatorService = new ActuatorExecutionService(controlQueue, chamberStates, chamberSummaries, output);
        telemetry = new TelemetryPublisher(failure -> { throw failure; });
        telemetry.subscribe(new ConsoleLoggingObserver(sensorLogs::append, clock));
        telemetry.subscribe(new ThresholdEvaluationObserver(cropRegistry,
                new DefaultCropThresholdStrategy(cropRegistry, clock), alerts, actuatorService, resources,
                chamberStates, chamberSummaries, Duration.ofSeconds(1), output));
        reports = new DailyReportService(resources, alerts, chamberSummaries,
                new PlainTextReportFormatter(), new FileReportStorageAdapter(reportPath), clock, output);
    }

    public void runSimulation(int iterationsPerChamber) throws IOException, InterruptedException {
        if (iterationsPerChamber < 0) {
            throw new IllegalArgumentException("iterationsPerChamber must be non-negative");
        }
        ExecutorService executor;
        synchronized (lifecycle) {
            if (closing) throw new IllegalStateException("Application context is closed");
            if (!simulationStarted.compareAndSet(false, true)) throw new IllegalStateException("Simulation has already started");
            for (ChamberSetup chamber : STANDARD_CHAMBERS) getChamberState(chamber.id());
            startActuators();
            executor = Executors.newVirtualThreadPerTaskExecutor();
            simulationExecutor = executor;
        }
        List<Future<?>> tasks = new ArrayList<>();
        Throwable primary = null;
        try {
            var completions = new ExecutorCompletionService<Void>(executor);
            for (int index = 0; index < STANDARD_CHAMBERS.size(); index++) {
                ChamberSetup setup = STANDARD_CHAMBERS.get(index);
                long seed = randomSeed + 0x9E3779B97F4A7C15L * index;
                RandomGenerator random = new SplittableRandom(seed);
                tasks.add(completions.submit(new ChamberSimulationTask(new ChamberId(setup.id()), setup.crop(),
                        iterationsPerChamber, telemetry, resources, random, clock), null));
            }
            for (int index = 0; index < tasks.size(); index++) {
                try {
                    completions.take().get();
                } catch (ExecutionException failure) {
                    throw new IllegalStateException("Chamber simulation failed", failure.getCause());
                }
            }
            synchronized (lifecycle) {
                if (closing) throw new IllegalStateException("Application was closed during simulation");
            }
        } catch (InterruptedException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            tasks.forEach(task -> task.cancel(true));
            try { close(); }
            catch (RuntimeException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
            if (primary instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted during simulation shutdown");
        reports.recordDailyReport();
        reports.archiveAlerts();
        output.accept("System finished sensor processing.");
        saveArchiveReport();
    }

    public CropRegistry cropRegistry() { return cropRegistry; }
    public ResourceAccountingService resources() { return resources; }
    public AlertManagementService alerts() { return alerts; }
    public Map<ChamberId, ChamberSnapshot> chamberStates() {
        return chamberStates.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().snapshot()));
    }
    public Map<ChamberId, ChamberSummarySnapshot> chamberSummaries() {
        return chamberSummaries.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().snapshot()));
    }
    public List<String> sensorLogs() { return sensorLogs.snapshot(); }
    public ActuatorExecutionService actuatorService() { return actuatorService; }
    /** Physical termination is separate from whether cleanup completed successfully. */
    public boolean isTerminated() {
        synchronized (lifecycle) {
            return cleanupFinished && (simulationExecutor == null || simulationExecutor.isTerminated())
                    && actuatorService.isTerminated();
        }
    }
    public TelemetryPublisher telemetry() { if (!closing) startActuators(); return telemetry; }
    public List<String> reportEntries() { return reports.reportEntries(); }

    private void startActuators() {
        synchronized (lifecycle) {
            if (closing) throw new IllegalStateException("Application context is closed");
            if (!actuatorsStarted) { actuatorService.start(); actuatorsStarted = true; }
        }
    }

    public ChamberSnapshot getChamberState(String chamber) {
        return chamberStates.computeIfAbsent(new ChamberId(chamber), ignored -> ChamberStateTracker.initial()).snapshot();
    }

    public ChamberSummarySnapshot getChamberSummary(String chamber) {
        return chamberSummaries.computeIfAbsent(new ChamberId(chamber), ignored -> new ChamberSummaryTracker()).snapshot();
    }

    /** Raw legacy input keeps its exact spelling in logs and matches sensor names case-sensitively. */
    public void processSensor(String chamber, String crop, String sensor, double value) {
        getChamberState(chamber);
        SensorType type = switch (sensor) {
            case "Temperature" -> SensorType.TEMPERATURE;
            case "Humidity" -> SensorType.HUMIDITY;
            case "Soil" -> SensorType.SOIL_MOISTURE;
            case "Light" -> SensorType.LIGHT;
            case "Nutrient" -> SensorType.NUTRIENT;
            default -> null;
        };
        if (type == null) {
            handleEvent("LOG", chamber, crop, "Processing " + crop + " " + sensor + " = " + value);
        } else {
            telemetry().publish(new SensorReading(new ChamberId(chamber), crop, type, value, clock.instant()));
        }
    }

    public void activateControl(String chamber, String control) {
        getChamberState(chamber);
        DeviceCommand command = commandFor(control);
        if (command != null) actuatorService.executeDirect(new ChamberId(chamber), command);
    }

    public void addControlRequest(String chamber, String control) {
        DeviceCommand command = commandFor(control);
        if (command == null) { getChamberState(chamber); return; }
        startActuators();
        try {
            if (!actuatorService.submit(new ActuatorRequest(new ChamberId(chamber), command), Duration.ofSeconds(5)))
                throw new IllegalStateException("Actuator queue remained full until timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queuing actuator work", interrupted);
        }
    }

    private static DeviceCommand commandFor(String control) {
        return switch (control) {
            case "Cooling Fan" -> DeviceCommandFactory.create(ActuatorType.COOLING_FAN);
            case "Water Pump" -> DeviceCommandFactory.create(ActuatorType.WATER_PUMP);
            case "Nutrient Injector" -> DeviceCommandFactory.create(ActuatorType.NUTRIENT_INJECTOR);
            case "Humidifier" -> DeviceCommandFactory.create(ActuatorType.HUMIDIFIER);
            case "Dehumidifier" -> DeviceCommandFactory.create(ActuatorType.DEHUMIDIFIER);
            case "Grow Light" -> DeviceCommandFactory.create(ActuatorType.GROW_LIGHT);
            default -> null;
        };
    }

    public void handleEvent(String type, String chamber, String crop, String message) {
        switch (type) {
            case "LOG" -> sensorLogs.append(new java.util.Date(clock.millis()) + " : " + message);
            case "ALERT_LOG" -> output.accept("[" + chamber + "][" + crop + "][ALERT] " + message);
            case "CONTROL_LOG" -> output.accept("\t\u001B[32m[" + chamber + "][CONTROL] " + message + "\u001B[0m");
            case "CREATE_ALERT" -> {
                alerts.publishAlert(new AlertEvent(new ChamberId(chamber), crop, AlertSeverity.WARNING, message, clock.instant()));
                handleEvent("ALERT_LOG", chamber, crop, message);
            }
            case "TRACK_ALERT" -> chamberSummaries.computeIfAbsent(new ChamberId(chamber), ignored -> new ChamberSummaryTracker()).recordAlert(message);
            case "TRACK_ACTION" -> chamberSummaries.computeIfAbsent(new ChamberId(chamber), ignored -> new ChamberSummaryTracker()).recordMitigationAction(message);
            default -> { }
        }
    }

    public void awaitControls(Duration timeout) throws InterruptedException { actuatorService.awaitIdle(timeout); }
    public void showLogs() { sensorLogs().forEach(output); }
    public void showAlerts() { alerts.getActiveAlertsSnapshot().forEach(alert -> output.accept(alert.message())); }
    public void irrigationCycle() { resources.irrigationCycle(); }
    public void feedingCycle() { resources.feedingCycle(); }
    public void generateDailyReport() { reports.recordDailyReport(); }
    public void archiveAlerts() { reports.archiveAlerts(); }
    public void saveArchiveReport() {
        try {
            reports.saveArchiveReport();
            output.accept("Archive report saved to: " + reportPath.toAbsolutePath());
        } catch (IOException failure) {
            output.accept("Could not save archive report: " + failure.getMessage());
        }
    }

    @Override
    public void close() {
        boolean interrupted = Thread.interrupted();
        boolean owner = false;
        synchronized (lifecycle) {
            if (!closing) {
                closing = true;
                cleanupDeadline = System.nanoTime() + shutdownBudget.toNanos();
                owner = true;
            }
        }
        RuntimeException outcome = null;
        try {
            if (owner) {
                ExecutorService executor = simulationExecutor;
                if (executor != null) {
                    executor.shutdownNow();
                    while (!executor.isTerminated()) {
                        long remaining = cleanupDeadline - System.nanoTime();
                        if (remaining <= 0) { outcome = new IllegalStateException("Chamber simulation workers did not terminate within shutdown budget"); break; }
                        try { executor.awaitTermination(remaining, TimeUnit.NANOSECONDS); }
                        catch (InterruptedException ignored) { interrupted = true; }
                    }
                }
                try { actuatorService.close(Duration.ofNanos(Math.max(0, cleanupDeadline - System.nanoTime()))); }
                catch (InterruptedException interruption) {
                    interrupted = true;
                    // Actor close restores interruption only after cleanup; a failed drain travels
                    // as suppressed evidence and must remain the shared context-close outcome.
                    for (Throwable suppressed : interruption.getSuppressed()) {
                        RuntimeException failed = suppressed instanceof RuntimeException runtime
                                ? runtime : new IllegalStateException("Actuator cleanup failed", suppressed);
                        if (outcome == null) outcome = failed;
                        else if (outcome != failed) outcome.addSuppressed(failed);
                    }
                }
                catch (RuntimeException failed) { if (outcome == null) outcome = failed; else outcome.addSuppressed(failed); }
                synchronized (lifecycle) {
                    cleanupFailure = outcome;
                    cleanupFinished = true;
                    lifecycle.notifyAll();
                }
            } else {
                synchronized (lifecycle) {
                    while (!cleanupFinished) {
                        long remaining = cleanupDeadline - System.nanoTime();
                        if (remaining <= 0) { outcome = new IllegalStateException("Timed out waiting for application shutdown"); break; }
                        try { TimeUnit.NANOSECONDS.timedWait(lifecycle, remaining); }
                        catch (InterruptedException ignored) { interrupted = true; }
                    }
                    if (cleanupFinished) outcome = cleanupFailure;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (outcome != null) throw outcome;
    }

    private record ChamberSetup(String id, String crop) { }
}
