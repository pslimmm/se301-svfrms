package org.example.svfrms.report;

import java.util.List;

/** The reports the system produces: the two legacy ones plus any optional extras. */
public record ReportSet(ReportGenerator daily, ReportGenerator archive, List<ReportGenerator> extras) {

    public ReportSet {
        extras = List.copyOf(extras);
    }
}
