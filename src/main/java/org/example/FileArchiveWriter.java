package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Preserves UTF-8, directory creation, and replacement of the previous daily report. */
public final class FileArchiveWriter implements ArchiveWriter {
    @Override public void write(Path target, List<String> lines) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.write(target, lines, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
