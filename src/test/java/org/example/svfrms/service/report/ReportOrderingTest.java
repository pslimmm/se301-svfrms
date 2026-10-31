package org.example.svfrms.service.report;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.example.svfrms.infrastructure.storage.ReportStoragePort;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class ReportOrderingTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void archiveCannotSplitDailyReportOutputInEitherOrder(boolean archiveFirst) throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        Consumer<String> output = line -> {
            lines.add(line);
            if (archiveFirst ? line.startsWith("Archiving") : line.equals("===== DAILY REPORT =====")) {
                entered.countDown(); await(release);
            }
        };
        var reports = reports(output, ignored -> {});
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { if (archiveFirst) reports.archiveAlerts(); else reports.recordDailyReport(); });
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> {
                    attempted.countDown();
                    if (archiveFirst) reports.recordDailyReport(); else reports.archiveAlerts();
                });
                assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
                try {
                    assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } finally { release.countDown(); }
                second.get(2, TimeUnit.SECONDS);
                first.get(2, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        int header = lines.indexOf("===== DAILY REPORT =====");
        assertThat(lines.subList(header, header + 5)).containsExactly("===== DAILY REPORT =====",
                "Generated Alerts: 0", "Active Alerts: 0", "Water Usage: 0 L", "Energy Usage: 0 kWh");
        assertThat(reports.reportEntries()).containsExactly(archiveFirst ? "No alerts to archive" : "Daily Report",
                archiveFirst ? "Daily Report" : "No alerts to archive");
    }

    @Test
    void blockedReportSinkDoesNotBlockSensorStateUpdatesAndCanInspectContext() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var current = new AtomicReference<SvfrmsApplicationContext>();
        try (var context = new SvfrmsApplicationContext(directory.resolve("report.txt"), Clock.systemUTC(), line -> {
            if (line.startsWith("===== DAILY")) {
                assertThat(current.get().getChamberState("CH5").temperature()).isEqualTo(24);
                current.get().sensorLogs();
                entered.countDown(); await(release);
            }
        }, 42); var pool = Executors.newFixedThreadPool(2)) {
            current.set(context);
            var reporter = pool.submit(context::generateDailyReport);
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                pool.submit(() -> context.processSensor("CH5", "Tomato", "Temperature", 25)).get(2, TimeUnit.SECONDS);
                assertThat(context.getChamberState("CH5").temperature()).isEqualTo(25);
            } finally { release.countDown(); }
            reporter.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void diskSaveDoesNotHoldPresentationLock() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var reports = reports(ignored -> {}, ignored -> { entered.countDown(); await(release); });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var save = pool.submit(reports::saveArchiveReport);
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                pool.submit(reports::archiveAlerts).get(2, TimeUnit.SECONDS);
                pool.submit(reports::recordDailyReport).get(2, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            save.get(2, TimeUnit.SECONDS);
        }
    }

    @org.junit.jupiter.api.RepeatedTest(20)
    void repeatedReportArchiveSchedulesRemainGrouped() throws Exception {
        archiveCannotSplitDailyReportOutputInEitherOrder(false);
        archiveCannotSplitDailyReportOutputInEitherOrder(true);
    }

    private static DailyReportService reports(Consumer<String> output, ReportStoragePort storage) {
        return new DailyReportService(new ResourceAccountingService(), new AlertManagementService(), Map.of(),
                new PlainTextReportFormatter(), storage, Clock.systemUTC(), output);
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("gate timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
}
