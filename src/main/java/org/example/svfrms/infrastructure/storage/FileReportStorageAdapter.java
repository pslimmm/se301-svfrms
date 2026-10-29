package org.example.svfrms.infrastructure.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** NIO adapter that stores UTF-8 report lines at a configured path. */
public final class FileReportStorageAdapter implements ReportStoragePort {
    private final Path reportFile;

    public FileReportStorageAdapter() {
        this(Path.of("archive", "daily_report.txt"));
    }

    public FileReportStorageAdapter(Path reportFile) {
        this.reportFile = Objects.requireNonNull(reportFile, "reportFile");
    }

    @Override
    public void writeLines(List<String> lines) throws IOException {
        Path parent = reportFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(reportFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public Path reportFile() {
        return reportFile;
    }
}
