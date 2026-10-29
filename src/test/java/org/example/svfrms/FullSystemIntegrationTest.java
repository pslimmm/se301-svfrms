package org.example.svfrms;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 9: Full System Integration & Composition Root Tests")
public class FullSystemIntegrationTest {

    @Test
    @DisplayName("SvfrmsApplication context bootstraps, executes simulation, and outputs daily report without deadlocks")
    void testFullSystemExecution() throws Exception {
        SvfrmsApplicationContext context = new SvfrmsApplicationContext();
        SvfrmsApplication app = new SvfrmsApplication(context);

        PrintStream origOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));

        try {
            app.runSimulation();
        } finally {
            System.setOut(origOut);
        }

        String stdout = capturedOut.toString();
        assertThat(stdout).contains("===== DAILY REPORT =====");
        assertThat(stdout).contains("Generated Alerts:");
        assertThat(stdout).contains("Water Usage:");
        assertThat(stdout).contains("Energy Usage:");
        assertThat(stdout).contains("System finished sensor processing.");
        assertThat(stdout).contains("Archive report saved to:");

        Path reportFile = Paths.get("archive", "daily_report.txt");
        assertThat(Files.exists(reportFile)).isTrue();
        List<String> lines = Files.readAllLines(reportFile);
        assertThat(lines.get(0)).isEqualTo("===== DAILY REPORT =====");
        assertThat(lines).contains("--- chamber wise summary ---");
        assertThat(lines).anyMatch(l -> l.startsWith("CH1 -> Alerts="));
        assertThat(lines).anyMatch(l -> l.startsWith("CH2 -> Alerts="));
        assertThat(lines).anyMatch(l -> l.startsWith("CH3 -> Alerts="));
        assertThat(lines).anyMatch(l -> l.startsWith("CH4 -> Alerts="));
    }
}
