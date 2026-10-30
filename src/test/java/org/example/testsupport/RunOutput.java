package org.example.testsupport;

/**
 * Everything observable about one run: what was printed, what went into the
 * sensor log (timestamps stripped) and the archive report file (timestamp masked).
 */
public record RunOutput(String console, String sensorLog, String archiveReport) {

    public static String maskTimestamp(String report) {
        return report.replaceAll("(?m)^Timestamp: .*$", "Timestamp: <masked>");
    }

    public static String maskSavedPath(String console) {
        return console.replaceAll("(?m)^Archive report saved to: .*$", "Archive report saved to: <path>");
    }

    /** Legacy log lines look like "Fri Oct 30 18:17:37 SGT 2026 : Processing ...". */
    public static String stripLogDate(String line) {
        int split = line.indexOf(" : ");
        return split < 0 ? line : line.substring(split + 3);
    }
}
