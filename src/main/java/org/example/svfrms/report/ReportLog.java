package org.example.svfrms.report;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of report events ("Archived 5 alerts", "Daily Report") written into the archive file
 * (legacy {@code reports}). Synchronized because the archive step and the report step used to run on
 * different threads (C11).
 */
public final class ReportLog {

    private final List<String> entries = new ArrayList<>();

    public synchronized void add(String entry) {
        entries.add(entry);
    }

    public synchronized List<String> entries() {
        return List.copyOf(entries);
    }
}
