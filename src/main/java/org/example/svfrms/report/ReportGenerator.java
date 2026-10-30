package org.example.svfrms.report;

import java.util.List;

/** Turns a facility snapshot into report lines. Where the lines go is a separate job ({@link ReportWriter}). */
@FunctionalInterface
public interface ReportGenerator {

    List<String> generate(FacilitySnapshot snapshot);
}
