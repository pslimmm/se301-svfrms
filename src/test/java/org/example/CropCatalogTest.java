package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.example.DeviceType.COOLING_FAN;
import static org.example.SensorType.TEMPERATURE;
import static org.example.ThresholdRule.Direction.ABOVE;
import static org.junit.jupiter.api.Assertions.*;

class CropCatalogTest {
    @TempDir Path directory;

    @Test
    void defaultsContainOnlyTheLegacyProfilesRulesAndAssignments() {
        CropCatalog catalog = CropCatalog.legacyDefaults();
        assertEquals(Set.of("Tomato", "Strawberry", "Lettuce", "Spinach", "Chili", "Cucumber"),
            catalog.profiles().keySet());
        assertEquals(17, catalog.profiles().values().stream().mapToInt(profile -> profile.rules().size()).sum());
        assertEquals(2, catalog.globalRules().size());
        assertEquals(List.of(
            new CropCatalog.ChamberAssignment("CH1", "Tomato"),
            new CropCatalog.ChamberAssignment("CH2", "Strawberry"),
            new CropCatalog.ChamberAssignment("CH3", "Lettuce"),
            new CropCatalog.ChamberAssignment("CH4", "Chili")), catalog.assignments());
        assertTrue(catalog.profile("tomato").isEmpty(), "crop lookup remains case-sensitive");
        for (String name : List.of("Lettuce", "Spinach", "Chili", "Cucumber")) {
            assertTrue(catalog.profile(name).orElseThrow().rules().stream()
                .allMatch(rule -> rule.sensor() == TEMPERATURE));
        }
        assertTrue(catalog.profile("Strawberry").orElseThrow().rules().stream()
            .noneMatch(rule -> rule.sensor() == SensorType.SOIL));
    }

    @Test
    void catalogAndProfilesDefensivelyCopyEveryConfigurationCollection() {
        List<ThresholdRule> rules = new ArrayList<>(List.of(fixtureRule()));
        CropProfile profile = new CropProfile("Fixture", rules);
        List<CropProfile> profiles = new ArrayList<>(List.of(profile));
        List<ThresholdRule> global = new ArrayList<>(CropCatalog.legacyDefaults().globalRules());
        List<CropCatalog.ChamberAssignment> assignments = new ArrayList<>(List.of(
            new CropCatalog.ChamberAssignment("CH1", "Fixture")));
        CropCatalog catalog = new CropCatalog(profiles, global, assignments);
        rules.clear();
        profiles.clear();
        global.clear();
        assignments.clear();
        assertEquals(1, catalog.profiles().size());
        assertEquals(List.of(fixtureRule()), catalog.profile("Fixture").orElseThrow().rules());
        assertEquals(2, catalog.globalRules().size());
        assertEquals(1, catalog.assignments().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.profiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.globalRules().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.assignments().clear());
        assertThrows(UnsupportedOperationException.class, () -> profile.rules().clear());
    }

    @Test
    void duplicateProfilesCannotSilentlyReplaceEachOther() {
        CropProfile profile = new CropProfile("Fixture", List.of(fixtureRule()));
        assertThrows(IllegalArgumentException.class,
            () -> new CropCatalog(List.of(profile, profile), List.of(), List.of()));
    }

    @Test
    void assignmentsRejectUnknownCropsAndDuplicateChambers() {
        CropProfile profile = new CropProfile("Fixture", List.of());
        assertThrows(IllegalArgumentException.class, () -> new CropCatalog(List.of(profile), List.of(),
            List.of(new CropCatalog.ChamberAssignment("CH1", "Missing"))));
        var assignment = new CropCatalog.ChamberAssignment("CH1", "Fixture");
        assertThrows(IllegalArgumentException.class, () -> new CropCatalog(List.of(profile), List.of(),
            List.of(assignment, assignment)));
    }

    @Test
    void invalidConfigurationIsRejectedWithoutValidatingIncomingMeasurements() {
        assertThrows(IllegalArgumentException.class, () -> new CropProfile(" ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CropCatalog.ChamberAssignment("", "Fixture"));
        assertThrows(NullPointerException.class, () -> new ThresholdRule(null, ABOVE, 23, "High", null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ThresholdRule(TEMPERATURE, ABOVE, 23, " ", null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ThresholdRule(TEMPERATURE, ABOVE, 23, "High", null, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ThresholdRule(TEMPERATURE, ABOVE, 23, "High", null, 0, -1));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void ruleThresholdsMustBeFinite(double threshold) {
        assertThrows(IllegalArgumentException.class,
            () -> new ThresholdRule(TEMPERATURE, ABOVE, threshold, "High", null, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 6})
    @Timeout(15)
    void fullRunUsesConfiguredProfilesAndAssignmentCount(int chambers) throws Exception {
        CropProfile fixture = new CropProfile("Fixture", List.of(fixtureRule()));
        List<CropCatalog.ChamberAssignment> assignments = IntStream.range(0, chambers)
            .mapToObj(i -> new CropCatalog.ChamberAssignment("Zone|" + i, "Fixture")).toList();
        CropCatalog catalog = new CropCatalog(List.of(fixture), List.of(), assignments);
        Random samples = new Random(301) {
            @Override public int nextInt(int bound) {
                return switch (bound) {
                    case 30 -> 14; // temperature 24: one fixture alert per iteration
                    case 60 -> 30;
                    case 900 -> 300;
                    case 80 -> 50;
                    default -> throw new AssertionError(bound);
                };
            }
        };
        Path report = directory.resolve("report.txt");
        SVFRMS app = new SVFRMS(catalog, samples, message -> {}, report);
        app.run();
        var snapshot = app.snapshot();
        assertEquals(SVFRMS.RunState.COMPLETED, app.runState());
        assertTrue(app.workersTerminated());
        assertEquals(100 * chambers, snapshot.sensorLogs().size());
        assertEquals(20 * chambers, snapshot.totalAlertsGenerated());
        assertEquals(20 * chambers, snapshot.archivedAlerts().size());
        assertEquals(0, snapshot.totalAlerts());
        assertEquals(100 * chambers, snapshot.totalWaterUsage()); // (2 + 3) * 20 per chamber
        assertEquals(160 * chambers, snapshot.totalEnergyUsage()); // (1 + 7) * 20 per chamber
        assertEquals(chambers, snapshot.chamberStates().size());
        for (var assignment : assignments) {
            var summary = snapshot.chamberSummaries().get(assignment.chamberId());
            assertEquals(20, summary.alerts());
            assertEquals(20, summary.mitigationActions());
            assertEquals(assignment.chamberId() + " Fixture High Temp", summary.lastAlert());
        }
        assertTrue(app.pendingControls().isEmpty());
        assertTrue(Files.readString(report).contains("Generated Alerts: " + (20 * chambers)));
    }

    private static ThresholdRule fixtureRule() {
        // Deliberately synthetic test data, not recommended growing conditions.
        return new ThresholdRule(TEMPERATURE, ABOVE, 23, "High Temp", COOLING_FAN, 3, 7);
    }
}
