package org.example.svfrms.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.example.svfrms.chamber.ChamberSummarySnapshot;
import org.example.svfrms.model.Alert;
import org.example.svfrms.model.AlertSeverity;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.SensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportsTest {

    private static final Alert HIGH_TEMP = new Alert("CH1", "Tomato", SensorType.TEMPERATURE, Breach.HIGH,
            AlertSeverity.WARNING, "CH1 Tomato High Temp");
    private static final Alert LOW_NUTRIENT = new Alert("CH2", "Kale", SensorType.NUTRIENT, Breach.LOW,
            AlertSeverity.WARNING, "CH2 Low Nutrient Level");

    private static FacilitySnapshot snapshot(List<Alert> archived, TreeMap<String, ChamberSummarySnapshot> chambers) {
        return new FacilitySnapshot("Fri Oct 30 12:00:00 SGT 2026", archived.size(), List.of(), archived,
                150, 42, List.of("Archived 2 alerts", "Daily Report"), chambers,
                Map.of("Water Pump", 3L, "Nutrient Injector", 1L, "Cooling Fan", 2L));
    }

    @Test
    void dailyReportHasLegacyLayout() {
        List<String> lines = new DailyReport().generate(snapshot(List.of(HIGH_TEMP), new TreeMap<>()));
        assertEquals(List.of("===== DAILY REPORT =====", "Generated Alerts: 1", "Active Alerts: 0",
                "Water Usage: 150 L", "Energy Usage: 42 kWh"), lines);
    }

    @Test
    void archiveReportListsChambersAndArchivedAlerts() {
        TreeMap<String, ChamberSummarySnapshot> chambers = new TreeMap<>();
        chambers.put("CH1", new ChamberSummarySnapshot(1, 1, "Cooling Fan Activated", "CH1 Tomato High Temp"));
        List<String> lines = new ArchiveReport().generate(snapshot(List.of(HIGH_TEMP, LOW_NUTRIENT), chambers));
        assertEquals(List.of(
                "===== DAILY REPORT =====",
                "Timestamp: Fri Oct 30 12:00:00 SGT 2026",
                "Generated Alerts: 2",
                "Active Alerts: 0",
                "Water Usage: 150 L",
                "Energy Usage: 42 kWh",
                "Archived alerts: 2",
                "---",
                "Archived 2 alerts",
                "Daily Report",
                "--- chamber wise summary ---",
                "CH1 -> Alerts=1, Actions=1, Last Action=Cooling Fan Activated, Last Alert=CH1 Tomato High Temp",
                "--- archived alerts ---",
                "CH1 Tomato High Temp",
                "CH2 Low Nutrient Level"), lines);
    }

    @Test
    void archiveReportSkipsEmptySectionsLikeLegacy() {
        List<String> lines = new ArchiveReport().generate(snapshot(List.of(), new TreeMap<>()));
        assertEquals("Daily Report", lines.getLast());
    }

    @Test
    void extraReports() {
        FacilitySnapshot s = snapshot(List.of(HIGH_TEMP, LOW_NUTRIENT), new TreeMap<>());
        assertEquals(List.of("===== WATER USAGE REPORT =====", "Total Water Usage: 150 L",
                "Water Pump activations: 3", "Humidifier activations: 0"), new WaterUsageReport().generate(s));
        assertEquals(List.of("===== NUTRIENT CONSUMPTION REPORT =====", "Nutrient Deficiency alerts: 1",
                "Nutrient Injector activations: 1"), new NutrientConsumptionReport().generate(s));
        assertEquals(List.of("===== WEEKLY CROP REPORT =====", "Kale -> Alerts=1", "Tomato -> Alerts=1"),
                new WeeklyCropReport().generate(s));
        assertEquals("Cooling Fan activations: 2", new EnergyConsumptionReport().generate(s).get(2));
    }

    @Test
    void fileWriterCreatesFolderAndOverwrites(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("daily_report.txt");
        FileReportWriter writer = new FileReportWriter(file);
        writer.write(List.of("first", "run"));
        writer.write(List.of("second"));
        assertEquals(List.of("second"), Files.readAllLines(file));
    }
}
