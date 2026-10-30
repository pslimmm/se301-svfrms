package org.example.svfrms.report;

import java.io.IOException;
import java.util.List;

/** Sends report lines somewhere (console, file, ...). Report content and report output are separate jobs (SRP). */
public interface ReportWriter {

    void write(List<String> lines) throws IOException;

    /** Human-readable destination, used in messages like "Archive report saved to: ...". */
    String location();
}
