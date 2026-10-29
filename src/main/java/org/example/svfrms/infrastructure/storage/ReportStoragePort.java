package org.example.svfrms.infrastructure.storage;

import java.io.IOException;
import java.util.List;

public interface ReportStoragePort {
    void writeLines(List<String> lines) throws IOException;
}
