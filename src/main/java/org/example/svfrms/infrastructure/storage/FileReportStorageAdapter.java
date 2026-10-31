package org.example.svfrms.infrastructure.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes a complete UTF-8 report without streaming concurrent writes into its destination. */
public final class FileReportStorageAdapter implements ReportStoragePort {
    private final Path reportFile;
    private final FileOperations files;

    public FileReportStorageAdapter() {
        this(Path.of("archive", "daily_report.txt"));
    }

    public FileReportStorageAdapter(Path reportFile) {
        this(reportFile, new NioFileOperations());
    }

    FileReportStorageAdapter(Path reportFile, FileOperations files) {
        this.reportFile = Objects.requireNonNull(reportFile, "reportFile");
        this.files = Objects.requireNonNull(files, "files");
    }

    @Override
    public void writeLines(List<String> lines) throws IOException {
        Path target = reportFile.toAbsolutePath();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = files.createTemp(parent);
        Throwable primary = null;
        try {
            files.writeUtf8(temporary, lines);
            try {
                files.replace(temporary, target, true);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Never request non-atomic replacement: providers may delete the old target
                // before a later move failure. Create only; an existing target fails visibly.
                files.replace(temporary, target, false);
            }
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try { files.deleteIfExists(temporary); }
            catch (IOException cleanup) {
                if (primary == null) throw cleanup;
                primary.addSuppressed(cleanup);
            }
        }
    }

    public Path reportFile() {
        return reportFile;
    }

    /** Filesystem boundary for deterministic unsupported-move and disk-failure verification. */
    interface FileOperations {
        Path createTemp(Path directory) throws IOException;
        void writeUtf8(Path file, List<String> lines) throws IOException;
        void replace(Path temporary, Path target, boolean atomic) throws IOException;
        void deleteIfExists(Path file) throws IOException;
    }

    static final class NioFileOperations implements FileOperations {
        public Path createTemp(Path directory) throws IOException {
            return Files.createTempFile(directory, ".svfrms-report-", ".tmp");
        }
        public void writeUtf8(Path file, List<String> lines) throws IOException {
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        public void replace(Path temporary, Path target, boolean atomic) throws IOException {
            if (atomic) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target);
        }
        public void deleteIfExists(Path file) throws IOException { Files.deleteIfExists(file); }
    }
}
