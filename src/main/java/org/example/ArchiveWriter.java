package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Persistence boundary; reporting owns formatting and error presentation. */
@FunctionalInterface
public interface ArchiveWriter {
    void write(Path target, List<String> lines) throws IOException;
}
