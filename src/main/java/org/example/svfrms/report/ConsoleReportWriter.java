package org.example.svfrms.report;

import java.io.PrintStream;
import java.util.List;

/** Prints report lines to the console. */
public final class ConsoleReportWriter implements ReportWriter {

    private final PrintStream out;

    public ConsoleReportWriter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void write(List<String> lines) {
        synchronized (out) {
            lines.forEach(out::println);
        }
    }

    @Override
    public String location() {
        return "console";
    }
}
