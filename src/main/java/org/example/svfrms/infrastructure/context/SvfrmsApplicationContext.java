package org.example.svfrms.infrastructure.context;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.concurrency.ChamberSimulationTask;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.SplittableRandom;

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
    private final CopyOnWriteArrayList<String> sensorLogs = new CopyOnWriteArrayList<>();
    private final TelemetryPublisher telemetry;
    private final ActuatorExecutionService actuatorService;
    private final DailyReportService reports;
    private final AtomicBoolean simulationStarted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile ExecutorService simulationExecutor;

    public SvfrmsApplicationContext() {
        this(Path.of("archive", "daily_report.txt"), Clock.systemDefaultZone(), System.out::println, 42L);
    }

    public SvfrmsApplicationContext(Path reportPath, Clock clock, Consumer<String> output, long randomSeed) {
        this.reportPath = Objects.requireNonNull(reportPath, "reportPath");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.output = Objects.requireNonNull(output, "output");
        this.randomSeed = randomSeed;
        cropRegistry = CropRegistry.withStandardProfiles();
        resources = new ResourceAccountingService();
        alerts = new AlertManagementService();

        Map<ChamberId, ChamberStateTracker> states = new LinkedHashMap<>();
        Map<ChamberId, ChamberSummaryTracker> summaries = new LinkedHashMap<>();
        for (ChamberSetup chamber : STANDARD_CHAMBERS) {
            ChamberId id = new ChamberId(chamber.id());
            states.put(id, new ChamberStateTracker(new ChamberSnapshot(24, 70, 60, 600, 70)));
            summaries.put(id, new ChamberSummaryTracker());
        }
        chamberStates = Map.copyOf(states);
        chamberSummaries = Map.copyOf(summaries);

        BoundedControlQueue controlQueue = new BoundedControlQueue();
        actuatorService = new ActuatorExecutionService(controlQueue, chamberStates, chamberSummaries, resources, output);
        telemetry = new TelemetryPublisher(failure -> output.accept("Telemetry observer failed: " + failure.getMessage()));
        telemetry.subscribe(new ConsoleLoggingObserver(sensorLogs::add, clock));
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
        if (closed.get()) {
            throw new IllegalStateException("Application context is closed");
        }
        if (!simulationStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("Simulation has already started");
        }

        actuatorService.start();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        simulationExecutor = executor;
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int index = 0; index < STANDARD_CHAMBERS.size(); index++) {
                ChamberSetup setup = STANDARD_CHAMBERS.get(index);
                long seed = randomSeed + 0x9E3779B97F4A7C15L * index;
                RandomGenerator random = new SplittableRandom(seed);
                tasks.add(executor.submit(new ChamberSimulationTask(new ChamberId(setup.id()), setup.crop(),
                        iterationsPerChamber, telemetry, resources, random, clock)));
            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (ExecutionException failure) {
                    throw new IllegalStateException("Chamber simulation failed", failure.getCause());
                }
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    output.accept("Chamber simulation workers did not terminate promptly");
                }
            }
            simulationExecutor = null;
        }

        actuatorService.close();
        reports.recordDailyReport();
        reports.archiveAlerts();
        reports.saveArchiveReport();
        output.accept("System finished sensor processing.");
        output.accept("Archive report saved to: " + reportPath.toAbsolutePath());
    }

    public CropRegistry cropRegistry() { return cropRegistry; }
    public ResourceAccountingService resources() { return resources; }
    public AlertManagementService alerts() { return alerts; }
    public Map<ChamberId, ChamberStateTracker> chamberStates() { return chamberStates; }
    public Map<ChamberId, ChamberSummaryTracker> chamberSummaries() { return chamberSummaries; }
    public List<String> sensorLogs() { return List.copyOf(sensorLogs); }
    public ActuatorExecutionService actuatorService() { return actuatorService; }
    public TelemetryPublisher telemetry() { return telemetry; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ExecutorService executor = simulationExecutor;
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            actuatorService.close();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record ChamberSetup(String id, String crop) { }
}
