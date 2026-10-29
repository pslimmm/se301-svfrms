package org.example.svfrms.infrastructure.context;

import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.infrastructure.storage.FileReportStorageAdapter;
import org.example.svfrms.infrastructure.storage.ReportStoragePort;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.CropThresholdStrategy;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.report.DailyReportService;
import org.example.svfrms.service.report.PlainTextReportFormatter;
import org.example.svfrms.service.report.ReportFormatterStrategy;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.ConsoleLoggingObserver;
import org.example.svfrms.service.telemetry.StateTrackingObserver;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.example.svfrms.service.telemetry.ThresholdEvaluationObserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composition Root wiring all domain, service, and infrastructure components via Pure Dependency Injection.
 */
public class SvfrmsApplicationContext {

    private final CropRegistry cropRegistry;
    private final CropThresholdStrategy cropStrategy;
    private final BoundedControlQueue controlQueue;
    private final ResourceAccountingService metrics;
    private final Map<ChamberId, ChamberStateTracker> stateMap;
    private final Map<ChamberId, ChamberSummaryTracker> summaryMap;
    private final AlertManagementService alertService;
    private final ReportStoragePort storagePort;
    private final ReportFormatterStrategy reportFormatter;
    private final DailyReportService reportService;
    private final TelemetryPublisher telemetryPublisher;
    private final ConsoleLoggingObserver consoleLogger;
    private final ActuatorExecutionService actuatorService;

    public SvfrmsApplicationContext() {
        this.cropRegistry = new CropRegistry();
        this.cropRegistry.registerDefaults();

        this.cropStrategy = new DefaultCropThresholdStrategy();
        this.controlQueue = new BoundedControlQueue(1024);
        this.metrics = new ResourceAccountingService();
        this.stateMap = new ConcurrentHashMap<>();
        this.summaryMap = new ConcurrentHashMap<>();

        // Initialize default chambers CH1-CH4
        for (int i = 1; i <= 4; i++) {
            ChamberId id = ChamberId.of("CH" + i);
            stateMap.put(id, new ChamberStateTracker(id));
            summaryMap.put(id, new ChamberSummaryTracker(id));
        }

        this.alertService = new AlertManagementService();
        this.storagePort = new FileReportStorageAdapter();
        this.reportFormatter = new PlainTextReportFormatter();
        this.reportService = new DailyReportService(metrics, alertService, summaryMap, reportFormatter, storagePort);

        this.telemetryPublisher = new TelemetryPublisher();
        this.consoleLogger = new ConsoleLoggingObserver();

        // Wire Observers (GoF Observer Pattern)
        this.telemetryPublisher.registerObserver(consoleLogger);
        this.telemetryPublisher.registerObserver(new StateTrackingObserver(stateMap));
        this.telemetryPublisher.registerObserver(new ThresholdEvaluationObserver(
                cropRegistry, cropStrategy, controlQueue, metrics, summaryMap, alertService::publishAlert
        ));

        // Wire Actuator Execution Service (GoF Command Pattern consumer)
        this.actuatorService = new ActuatorExecutionService(controlQueue, stateMap, summaryMap, metrics);
    }

    public CropRegistry getCropRegistry() {
        return cropRegistry;
    }

    public CropThresholdStrategy getCropStrategy() {
        return cropStrategy;
    }

    public BoundedControlQueue getControlQueue() {
        return controlQueue;
    }

    public ResourceAccountingService getMetrics() {
        return metrics;
    }

    public Map<ChamberId, ChamberStateTracker> getStateMap() {
        return stateMap;
    }

    public Map<ChamberId, ChamberSummaryTracker> getSummaryMap() {
        return summaryMap;
    }

    public AlertManagementService getAlertService() {
        return alertService;
    }

    public ReportStoragePort getStoragePort() {
        return storagePort;
    }

    public ReportFormatterStrategy getReportFormatter() {
        return reportFormatter;
    }

    public DailyReportService getReportService() {
        return reportService;
    }

    public TelemetryPublisher getTelemetryPublisher() {
        return telemetryPublisher;
    }

    public ConsoleLoggingObserver getConsoleLogger() {
        return consoleLogger;
    }

    public ActuatorExecutionService getActuatorService() {
        return actuatorService;
    }
}
