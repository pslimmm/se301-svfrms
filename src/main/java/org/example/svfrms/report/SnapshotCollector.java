package org.example.svfrms.report;

import java.time.Clock;
import java.util.Date;

import org.example.svfrms.alert.AlertService;
import org.example.svfrms.alert.AlertSnapshot;
import org.example.svfrms.chamber.ChamberSummaryTracker;
import org.example.svfrms.device.DeviceActivationCounter;
import org.example.svfrms.metrics.ResourceUsageTracker;

/** Gathers a {@link FacilitySnapshot} from the live components. */
public final class SnapshotCollector {

    private final AlertService alerts;
    private final ResourceUsageTracker usage;
    private final ReportLog reportLog;
    private final ChamberSummaryTracker summaries;
    private final DeviceActivationCounter activations;
    private final Clock clock;

    public SnapshotCollector(AlertService alerts, ResourceUsageTracker usage, ReportLog reportLog,
                             ChamberSummaryTracker summaries, DeviceActivationCounter activations, Clock clock) {
        this.alerts = alerts;
        this.usage = usage;
        this.reportLog = reportLog;
        this.summaries = summaries;
        this.activations = activations;
        this.clock = clock;
    }

    public FacilitySnapshot capture() {
        AlertSnapshot alertState = alerts.snapshot();
        return new FacilitySnapshot(
                new Date(clock.millis()).toString(),
                alertState.totalGenerated(),
                alertState.active(),
                alertState.archived(),
                usage.water(),
                usage.energy(),
                reportLog.entries(),
                summaries.snapshot(),
                activations.snapshot());
    }
}
