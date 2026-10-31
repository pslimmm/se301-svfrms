package org.example.svfrms.service.report;

import java.util.List;

public interface ReportFormatterStrategy {
    void formatHeader(DailyReportSnapshot snapshot, List<String> lines);

    void formatBody(DailyReportSnapshot snapshot, List<String> lines);
}
