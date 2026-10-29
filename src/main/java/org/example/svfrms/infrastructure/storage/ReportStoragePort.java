package org.example.svfrms.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Port interface for persistent storage of system reports (Hexagonal / Ports & Adapters Architecture).
 */
public interface ReportStoragePort {

    /**
     * Persists a collection of formatted report lines to the specified destination path.
     *
     * @param destination the destination file path
     * @param lines       the report lines to write
     * @throws IOException if an I/O error occurs
     */
    void saveReport(Path destination, List<String> lines) throws IOException;
}
