package org.example.svfrms.service.report;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for formatting system report data into target output representations (GoF Strategy Pattern).
 */
public interface ReportFormatterStrategy {

    List<String> formatReport(
            Date timestamp,
            long totalAlertsGenerated,
            long activeAlerts,
            long waterUsage,
            long energyUsage,
            List<String> reportEvents,
            Map<ChamberId, ChamberSummarySnapshot> chamberSummaries,
            List<AlertEvent> archivedAlerts);
}
