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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Coordinates operational summary output, alert archival, and saved daily reports. */
public final class DailyReportService extends AbstractReportGenerator {
    private final ResourceAccountingService resources;
    private final AlertManagementService alerts;
    private final Map<ChamberId, ChamberSummaryTracker> chamberSummaries;
    private final Clock clock;
    private final Consumer<String> output;
    private final CopyOnWriteArrayList<String> reportEntries = new CopyOnWriteArrayList<>();

    public DailyReportService(ResourceAccountingService resources, AlertManagementService alerts,
                             Map<ChamberId, ChamberSummaryTracker> chamberSummaries,
                             ReportFormatterStrategy formatter, ReportStoragePort storage,
                             Clock clock, Consumer<String> output) {
        super(formatter, storage);
        this.resources = Objects.requireNonNull(resources, "resources");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.chamberSummaries = Map.copyOf(chamberSummaries);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void recordReportEntry(String entry) {
        reportEntries.add(Objects.requireNonNull(entry, "entry"));
    }

    public void recordDailyReport() {
        AlertSnapshot alertSnapshot = alerts.snapshot();
        recordReportEntry("Daily Report");
        output.accept("===== DAILY REPORT =====");
        output.accept("Generated Alerts: " + alertSnapshot.generatedAlertCount());
        output.accept("Active Alerts: " + alertSnapshot.activeAlerts().size());
        output.accept("Water Usage: " + resources.waterUsage() + " L");
        output.accept("Energy Usage: " + resources.energyUsage() + " kWh");
    }

    public List<AlertEvent> archiveAlerts() {
        List<AlertEvent> archived = alerts.archiveAlerts();
        if (archived.isEmpty()) {
            recordReportEntry("No alerts to archive");
            output.accept("Archiving alerts... no active alerts to archive.");
        } else {
            recordReportEntry("Archived " + archived.size() + " alerts");
            output.accept("Archiving alerts... " + archived.size() + " moved to archive.");
        }
        return archived;
    }

    public List<String> saveArchiveReport() throws java.io.IOException {
        return generate();
    }

    @Override
    protected DailyReportSnapshot extractMetrics() {
        Map<ChamberId, org.example.svfrms.domain.chamber.ChamberSummarySnapshot> summarySnapshots = chamberSummaries.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().snapshot()));
        AlertSnapshot alertSnapshot = alerts.snapshot();
        return new DailyReportSnapshot(clock.instant(), alertSnapshot.generatedAlertCount(),
                alertSnapshot.activeAlerts().size(), resources.waterUsage(), resources.energyUsage(),
                reportEntries, summarySnapshots, alertSnapshot.archivedAlerts());
    }
}
