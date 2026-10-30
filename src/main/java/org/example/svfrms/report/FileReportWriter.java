package org.example.svfrms.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Writes report lines to a file, replacing the old contents (same options as the legacy code). */
public final class FileReportWriter implements ReportWriter {

    private final Path file;

    public FileReportWriter(Path file) {
        this.file = file;
    }

    @Override
    public void write(List<String> lines) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    @Override
    public String location() {
        return file.toAbsolutePath().toString();
    }
}
