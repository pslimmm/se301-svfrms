package org.example.svfrms;

import org.example.SVFRMS;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 1: Legacy Baseline Golden Master Characterization Test")
public class GoldenMasterLegacyTest {

    @Test
    @DisplayName("Verify legacy SVFRMS main execution generates valid daily report and expected structure")
    void testLegacySystemExecutionBaseline() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));

        try {
            // Execute the legacy system entry point
            SVFRMS.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String stdout = capturedOut.toString();

        // 1. Verify stdout contains essential operational lifecycle markers
        assertThat(stdout).contains("===== DAILY REPORT =====");
        assertThat(stdout).contains("Generated Alerts:");
        assertThat(stdout).contains("Water Usage:");
        assertThat(stdout).contains("Energy Usage:");
        assertThat(stdout).contains("System finished sensor processing.");
        assertThat(stdout).contains("Archive report saved to:");

        // 2. Verify archive file was written to disk
        Path reportFile = Paths.get("archive", "daily_report.txt");
        assertThat(Files.exists(reportFile)).isTrue();

        List<String> lines = Files.readAllLines(reportFile);
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).isEqualTo("===== DAILY REPORT =====");
        assertThat(lines).anyMatch(line -> line.startsWith("Timestamp:"));
        assertThat(lines).anyMatch(line -> line.startsWith("Generated Alerts:"));
        assertThat(lines).anyMatch(line -> line.startsWith("Water Usage:"));
        assertThat(lines).anyMatch(line -> line.startsWith("Energy Usage:"));
        assertThat(lines).contains("--- chamber wise summary ---");
        assertThat(lines).anyMatch(line -> line.startsWith("CH1 -> Alerts="));
        assertThat(lines).anyMatch(line -> line.startsWith("CH2 -> Alerts="));
        assertThat(lines).anyMatch(line -> line.startsWith("CH3 -> Alerts="));
        assertThat(lines).anyMatch(line -> line.startsWith("CH4 -> Alerts="));
        assertThat(lines).contains("--- archived alerts ---");
    }
}
