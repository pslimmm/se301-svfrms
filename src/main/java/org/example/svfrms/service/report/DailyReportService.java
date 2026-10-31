package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.infrastructure.storage.ReportStoragePort;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.alert.AlertSnapshot;
import org.example.svfrms.service.resource.ResourceAccountingService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Coordinates operational summary output, alert archival, and saved daily reports. */
public final class DailyReportService extends AbstractReportGenerator {
    private final ResourceAccountingService resources;
    private final AlertManagementService alerts;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final Clock clock;
    private final Consumer<String> output;
    // This lock groups only report operations. Alert ingestion never acquires it.
    // Callbacks run without state locks; disk I/O occurs after extractMetrics returns.
    private final ReentrantLock operations = new ReentrantLock();
    private final List<String> reportEntries = new ArrayList<>();

    public DailyReportService(ResourceAccountingService resources, AlertManagementService alerts,
                             Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                             ReportFormatterStrategy formatter, ReportStoragePort storage,
                             Clock clock, Consumer<String> output) {
        super(formatter, storage);
        this.resources = Objects.requireNonNull(resources, "resources");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.chamberSummaries = Objects.requireNonNull(chamberSummaries, "chamberSummaries");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void recordReportEntry(String entry) {
        operations.lock();
        try {
            reportEntries.add(Objects.requireNonNull(entry, "entry"));

        } finally { operations.unlock(); }
    }

    public void recordDailyReport() {
        operations.lock();
        try {
            AlertSnapshot alertSnapshot = alerts.snapshot();
            recordReportEntry("Daily Report");
            output.accept("===== DAILY REPORT =====");
            output.accept("Generated Alerts: " + alertSnapshot.generatedAlertCount());
            output.accept("Active Alerts: " + alertSnapshot.activeAlerts().size());
            output.accept("Water Usage: " + resources.waterUsage() + " L");
            output.accept("Energy Usage: " + resources.energyUsage() + " kWh");

        } finally { operations.unlock(); }
    }

    public List<AlertEvent> archiveAlerts() {
        operations.lock();
        try {
            List<AlertEvent> archived = alerts.archiveAlerts();
            if (archived.isEmpty()) {
                recordReportEntry("No alerts to archive");
                output.accept("Archiving alerts... no active alerts to archive.");
            } else {
                recordReportEntry("Archived " + archived.size() + " alerts");
                output.accept("Archiving alerts... " + archived.size() + " moved to archive.");
            }
            return archived;

        } finally { operations.unlock(); }
    }

    public List<String> saveArchiveReport() throws java.io.IOException {
        return generate();
    }

    public List<String> reportEntries() {
        operations.lock();
        try { return List.copyOf(reportEntries); }
        finally { operations.unlock(); }
    }

    @Override
    protected DailyReportSnapshot extractMetrics() {
        operations.lock();
        try {
            Map<ChamberId, org.example.svfrms.domain.chamber.ChamberSummarySnapshot> summarySnapshots = chamberSummaries.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().snapshot()));
            AlertSnapshot alertSnapshot = alerts.snapshot();
            return new DailyReportSnapshot(clock.instant(), alertSnapshot.generatedAlertCount(),
                    alertSnapshot.activeAlerts().size(), resources.waterUsage(), resources.energyUsage(),
                    reportEntries, summarySnapshots, alertSnapshot.archivedAlerts());

        } finally { operations.unlock(); }
    }
}
