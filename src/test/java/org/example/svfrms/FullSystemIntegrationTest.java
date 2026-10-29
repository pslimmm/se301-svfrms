package org.example.svfrms;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class FullSystemIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void runsFourChambersArchivesAlertsAndGeneratesDailyReport() throws Exception {
        Path reportFile = tempDir.resolve("archive/daily_report.txt");
        var output = new CopyOnWriteArrayList<String>();
        SvfrmsApplicationContext context = new SvfrmsApplicationContext(
                reportFile, Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC), output::add, 42L);
        try {
            context.runSimulation(20);

            assertThat(context.chamberStates()).hasSize(4);
            assertThat(context.chamberSummaries()).hasSize(4);
            assertThat(context.sensorLogs()).hasSize(4 * 20 * 5);
            assertThat(context.resources().waterUsage()).isGreaterThanOrEqualTo(160);
            assertThat(context.resources().energyUsage()).isGreaterThanOrEqualTo(80);
            assertThat(context.alerts().generatedAlertCount()).isGreaterThan(0);
            assertThat(context.alerts().getActiveAlertsSnapshot()).isEmpty();
            assertThat(context.alerts().getArchivedAlertsSnapshot()).isNotEmpty();
            assertThat(context.actuatorService().isTerminated()).isTrue();
            assertThat(Files.readAllLines(reportFile, StandardCharsets.UTF_8))
                    .startsWith("===== DAILY REPORT =====")
                    .contains("--- chamber wise summary ---", "--- archived alerts ---");
            assertThat(output).contains("System finished sensor processing.");
        } finally {
            context.close();
        }
    }
}
