package org.example.svfrms.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.io.IOException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class FileReportStorageAdapterTest {
    @TempDir Path directory;

    @Test
    void concurrentSavesProduceOneCompleteCandidate() throws Exception {
        Path target = directory.resolve("report.txt");
        var aEntered = new CountDownLatch(1);
        var aRelease = new CountDownLatch(1);
        var bEntered = new CountDownLatch(1);
        var bRelease = new CountDownLatch(1);
        List<String> a = Collections.nCopies(2000, "A".repeat(100));
        List<String> b = Collections.nCopies(10, "B".repeat(100));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { new FileReportStorageAdapter(target).writeLines(gated(a, aEntered, aRelease)); return null; });
            try {
                assertThat(aEntered.await(2, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> { new FileReportStorageAdapter(target).writeLines(gated(b, bEntered, bRelease)); return null; });
                try {
                    assertThat(bEntered.await(2, TimeUnit.SECONDS)).isTrue();
                    aRelease.countDown();
                    first.get(2, TimeUnit.SECONDS);
                } finally { bRelease.countDown(); }
                second.get(2, TimeUnit.SECONDS);
            } finally { aRelease.countDown(); bRelease.countDown(); }
        }
        assertThat(Files.readAllLines(target)).isEqualTo(b);
        try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).containsExactly("report.txt"); }
    }

    @Test
    void failedPreparationLeavesPreviousReportUntouched() throws Exception {
        Path target = directory.resolve("report.txt");
        Files.write(target, List.of("previous complete report"));
        var bad = new AbstractList<String>() {
            public int size() { return 2000; }
            public String get(int index) {
                if (index == 1000) throw new IllegalStateException("interrupted formatting source");
                return "partial".repeat(100);
            }
        };
        assertThatThrownBy(() -> new FileReportStorageAdapter(target).writeLines(bad)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readAllLines(target)).containsExactly("previous complete report");
        try (var files = Files.list(directory)) { assertThat(files.count()).isEqualTo(1); }
    }

    @Test
    void unicodeAndTruncationMatchLegacy() throws Exception {
        Path target = directory.resolve("nested/report.txt");
        var storage = new FileReportStorageAdapter(target);
        storage.writeLines(List.of("old", "stale tail"));
        storage.writeLines(List.of("生菜 α"));
        assertThat(Files.readAllLines(target, StandardCharsets.UTF_8)).containsExactly("生菜 α");
        assertThat(storage.reportFile()).isEqualTo(target);
    }

    @Test
    void unsupportedAtomicMoveCreatesNewReportButPreservesExistingReport() throws Exception {
        Path target = directory.resolve("fallback.txt");
        new FileReportStorageAdapter(target, operations(true, null, null)).writeLines(List.of("new complete"));
        assertThat(Files.readAllLines(target)).containsExactly("new complete");
        assertThatThrownBy(() -> new FileReportStorageAdapter(target, operations(true, null, null))
                .writeLines(List.of("replacement"))).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readAllLines(target)).containsExactly("new complete");
        try (var files = Files.list(directory)) { assertThat(files.count()).isEqualTo(1); }
    }

    @Test
    void realMoveFailurePropagatesAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("failure.txt");
        Files.write(target, List.of("previous"));
        IOException failure = new IOException("disk replacement failed");
        assertThatThrownBy(() -> new FileReportStorageAdapter(target, operations(false, failure, null))
                .writeLines(List.of("new"))).isSameAs(failure);
        assertThat(Files.readAllLines(target)).containsExactly("previous");
        try (var files = Files.list(directory)) { assertThat(files.count()).isEqualTo(1); }
    }

    @Test
    void cleanupFailureDoesNotHidePrimaryReplacementFailure() {
        IOException primary = new IOException("replacement");
        IOException cleanup = new IOException("cleanup");
        assertThatThrownBy(() -> new FileReportStorageAdapter(directory.resolve("failure.txt"),
                operations(false, primary, cleanup)).writeLines(List.of("new")))
                .isSameAs(primary).hasSuppressedException(cleanup);
    }

    @Test
    void cleanupFailureAfterPublicationRemainsVisible() throws Exception {
        Path target = directory.resolve("published.txt");
        IOException cleanup = new IOException("cleanup");
        assertThatThrownBy(() -> new FileReportStorageAdapter(target, operations(false, null, cleanup))
                .writeLines(List.of("complete"))).isSameAs(cleanup);
        assertThat(Files.readAllLines(target)).containsExactly("complete");
    }

    private static FileReportStorageAdapter.FileOperations operations(boolean unsupported, IOException moveFailure, IOException cleanupFailure) {
        var nio = new FileReportStorageAdapter.NioFileOperations();
        return new FileReportStorageAdapter.FileOperations() {
            public Path createTemp(Path parent) throws IOException { return nio.createTemp(parent); }
            public void writeUtf8(Path file, List<String> lines) throws IOException { nio.writeUtf8(file, lines); }
            public void replace(Path temporary, Path target, boolean atomic) throws IOException {
                if (moveFailure != null) throw moveFailure;
                if (unsupported && atomic) throw new AtomicMoveNotSupportedException(temporary.toString(), target.toString(), "test filesystem");
                nio.replace(temporary, target, atomic);
            }
            public void deleteIfExists(Path file) throws IOException {
                if (cleanupFailure != null) throw cleanupFailure;
                nio.deleteIfExists(file);
            }
        };
    }

    private static List<String> gated(List<String> lines, CountDownLatch entered, CountDownLatch release) {
        return new AbstractList<>() {
            public int size() { return lines.size(); }
            public String get(int index) {
                if (index == 0) {
                    entered.countDown();
                    try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("gate timed out"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                }
                return lines.get(index);
            }
        };
    }
}
