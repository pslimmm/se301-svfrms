package org.example.svfrms.service.report;

import org.example.svfrms.infrastructure.storage.ReportStoragePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Template Method defining extract, format, then persist for every report. */
public abstract class AbstractReportGenerator {
    private final ReportFormatterStrategy formatter;
    private final ReportStoragePort storage;

    protected AbstractReportGenerator(ReportFormatterStrategy formatter, ReportStoragePort storage) {
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public final List<String> generate() throws IOException {
        DailyReportSnapshot snapshot = extractMetrics();
        List<String> lines = new ArrayList<>();
        formatter.formatHeader(snapshot, lines);
        formatter.formatBody(snapshot, lines);
        List<String> immutableLines = List.copyOf(lines);
        storage.writeLines(immutableLines);
        return immutableLines;
    }

    protected abstract DailyReportSnapshot extractMetrics();
}
