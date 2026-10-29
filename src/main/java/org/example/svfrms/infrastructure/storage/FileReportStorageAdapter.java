package org.example.svfrms.infrastructure.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * File system adapter implementing ReportStoragePort via Java NIO.
 */
public class FileReportStorageAdapter implements ReportStoragePort {

    @Override
    public void saveReport(Path destination, List<String> lines) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.write(destination, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
}
