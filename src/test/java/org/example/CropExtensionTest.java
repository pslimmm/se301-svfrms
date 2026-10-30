package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.example.SensorType.*;
import static org.example.ThresholdRule.Direction.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class CropExtensionTest {
    @TempDir Path directory;

    @Test
    void registrationAndAssignmentReturnIndependentCatalogsWithAllDefaultsPreserved() {
        CropCatalog original = CropCatalog.legacyDefaults();
        CropCatalog extended = CropExtensionDemo.catalog();
        assertEquals(6, original.profiles().size());
        assertTrue(original.profile("Kale").isEmpty());
        assertEquals(7, extended.profiles().size());
        original.profiles().forEach((name, profile) -> assertEquals(profile, extended.profile(name).orElseThrow()));
        assertEquals(original.globalRules(), extended.globalRules());
        assertEquals(original.assignments(), extended.assignments().subList(0, 4));
        assertEquals(new CropCatalog.ChamberAssignment("CH5", "Kale"), extended.assignments().getLast());
        assertThrows(UnsupportedOperationException.class, () -> extended.assignments().clear());
        assertThrows(UnsupportedOperationException.class, () -> extended.profiles().clear());
    }

    @Test
    void duplicateRegistrationAndAssignmentsFailWithoutOverwritingConfiguration() {
        CropCatalog original = CropCatalog.legacyDefaults();
        assertThrows(IllegalArgumentException.class, () -> original.withProfile(new CropProfile("Tomato", List.of())));
        assertThrows(IllegalArgumentException.class, () -> original.withAssignment("CH1", "Spinach"));
        assertThrows(IllegalArgumentException.class, () -> original.withAssignment("CH5", "Missing"));
        assertThrows(IllegalArgumentException.class, () -> original.withAssignment(" ", "Spinach"));
        assertEquals(5, original.profile("Tomato").orElseThrow().rules().size());
        assertEquals("Tomato", original.assignments().getFirst().cropName());
        assertEquals(4, original.assignments().size());
    }

    @Test
    void unassignedProfileRemovalDoesNotAffectOriginalOrOtherProfiles() {
        CropCatalog original = CropCatalog.legacyDefaults();
        CropCatalog removed = original.withoutProfile("Spinach");
        assertTrue(removed.profile("Spinach").isEmpty());
        assertTrue(original.profile("Spinach").isPresent());
        assertEquals(original.assignments(), removed.assignments());
        assertEquals(original.globalRules(), removed.globalRules());
        assertEquals(5, removed.profiles().size());
        assertTrue(new RuleEvaluator(removed).evaluate("CH1", "Spinach", "Temperature", 30).isEmpty());
        assertEquals(1, new RuleEvaluator(removed).evaluate("CH1", "Spinach", "Light", 400).size(),
            "direct unknown-crop input retains legacy global fallback");
    }

    @Test
    void assignedProfileRemovalRequiresReassigningEveryChamberAndKeepsStartupOrder() {
        CropCatalog original = CropCatalog.legacyDefaults().withAssignment("CH5", "Tomato");
        assertThrows(IllegalArgumentException.class, () -> original.withoutProfile("Tomato"));
        CropCatalog partial = original.reassignChamber("CH1", "Spinach");
        assertThrows(IllegalArgumentException.class, () -> partial.withoutProfile("Tomato"));
        CropCatalog removed = partial.reassignChamber("CH5", "Cucumber").withoutProfile("Tomato");
        assertTrue(removed.profile("Tomato").isEmpty());
        assertEquals(List.of("CH1", "CH2", "CH3", "CH4", "CH5"),
            removed.assignments().stream().map(CropCatalog.ChamberAssignment::chamberId).toList());
        assertEquals("Spinach", removed.assignments().getFirst().cropName());
        assertEquals("Cucumber", removed.assignments().getLast().cropName());
        assertEquals("Tomato", original.assignments().getFirst().cropName());
        assertEquals(partial.assignments(), partial.reassignChamber("CH1", "Spinach").assignments());
    }

    @Test
    void unassigningTheLastChamberAllowsRemovalWithoutTouchingOtherAssignments() {
        CropCatalog original = CropExtensionDemo.catalog();
        CropCatalog removed = original.withoutAssignment("CH5").withoutProfile("Kale");
        assertEquals(CropCatalog.legacyDefaults().assignments(), removed.assignments());
        assertEquals(CropCatalog.legacyDefaults().profiles(), removed.profiles());
        assertEquals(5, original.assignments().size());
        CropCatalog empty = removed;
        for (var assignment : removed.assignments()) empty = empty.withoutAssignment(assignment.chamberId());
        assertTrue(empty.assignments().isEmpty());
    }

    @Test
    void unknownRemovalAndReassignmentTargetsAreExplicitErrors() {
        CropCatalog original = CropCatalog.legacyDefaults();
        assertThrows(IllegalArgumentException.class, () -> original.withoutProfile("tomato"));
        assertThrows(IllegalArgumentException.class, () -> original.withoutAssignment("Missing"));
        assertThrows(IllegalArgumentException.class, () -> original.reassignChamber("Missing", "Tomato"));
        assertThrows(IllegalArgumentException.class, () -> original.reassignChamber("CH1", "Missing"));
        assertThrows(NullPointerException.class, () -> original.withProfile(null));
        assertThrows(NullPointerException.class, () -> original.withoutProfile(null));
        assertThrows(NullPointerException.class, () -> original.withoutAssignment(null));
        assertEquals(4, original.assignments().size());
    }

    @ParameterizedTest
    @EnumSource(SensorType.class)
    void sameScopeDuplicateBoundariesAreRejectedEvenWithDifferentConsequences(SensorType sensor) {
        for (var direction : ThresholdRule.Direction.values()) {
            ThresholdRule first = new ThresholdRule(sensor, direction, 20, "First", null, 0, 0);
            for (double threshold : List.of(20.0, 21.0)) {
                ThresholdRule duplicate = new ThresholdRule(sensor, direction, threshold, "Second", null, 1, 2);
                assertThrows(IllegalArgumentException.class, () -> new CropProfile("Fixture", List.of(first, duplicate)));
                assertThrows(IllegalArgumentException.class, () -> new CropCatalog(List.of(), List.of(first, duplicate), List.of()));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"20, 10", "20, 20", "20, 19.999"})
    void invertedAndZeroWidthRangesAreRejectedInEitherOrderAndAcrossScopes(double lower, double upper) {
        ThresholdRule low = new ThresholdRule(TEMPERATURE, BELOW, lower, "Low", null, 0, 0);
        ThresholdRule high = new ThresholdRule(TEMPERATURE, ABOVE, upper, "High", null, 0, 0);
        for (List<ThresholdRule> order : List.of(List.of(low, high), List.of(high, low))) {
            assertThrows(IllegalArgumentException.class, () -> new CropProfile("Fixture", order));
            assertThrows(IllegalArgumentException.class, () -> new CropCatalog(List.of(), order, List.of()));
            CropProfile profile = new CropProfile("Fixture", List.of(order.getFirst()));
            assertThrows(IllegalArgumentException.class,
                () -> new CropCatalog(List.of(profile), List.of(order.getLast()), List.of()));
        }
    }

    @Test
    void emptyOneSidedAndOrderedRangesRemainValidWithoutImplicitRules() {
        assertTrue(new CropProfile("Empty", List.of()).rules().isEmpty());
        ThresholdRule low = new ThresholdRule(TEMPERATURE, BELOW, 16, "Low", null, 0, 0);
        ThresholdRule high = new ThresholdRule(TEMPERATURE, ABOVE, 24, "High", null, 0, 0);
        assertEquals(1, new CropProfile("OneSided", List.of(low)).rules().size());
        assertEquals(2, new CropProfile("TwoSided", List.of(high, low)).rules().size());
        CropCatalog catalog = new CropCatalog(List.of(new CropProfile("Fixture", List.of(low))), List.of(high), List.of());
        RuleEvaluator evaluator = new RuleEvaluator(catalog);
        for (double value : List.of(16.0, 20.0, 24.0)) {
            assertTrue(evaluator.evaluate("CH1", "Fixture", "Temperature", value).isEmpty());
        }
        assertEquals(1, evaluator.evaluate("CH1", "Fixture", "Temperature", 15).size());
        assertEquals(1, evaluator.evaluate("CH1", "Fixture", "Temperature", 25).size());
    }

    @ParameterizedTest
    @CsvSource({
        "Temperature, 15, Low Temp, 0, 0", "Temperature, 16, NONE, 0, 0", "Temperature, 17, NONE, 0, 0",
        "Temperature, 23, NONE, 0, 0", "Temperature, 24, NONE, 0, 0", "Temperature, 25, High Temp, 0, 3",
        "Soil, 39, Low Soil Moisture, 10, 0", "Soil, 40, NONE, 0, 0", "Soil, 41, NONE, 0, 0"
    })
    void kaleDemoHasExplicitStrictBoundariesAndCharges(String sensor, double value, String suffix, int water, int energy) {
        var effects = new RuleEvaluator(CropExtensionDemo.catalog()).evaluate("CH5", "Kale", sensor, value);
        if (suffix.equals("NONE")) {
            assertTrue(effects.isEmpty());
        } else {
            assertEquals(1, effects.size());
            var effect = effects.getFirst();
            assertEquals("CH5 Kale " + suffix, effect.alert());
            assertEquals(water, effect.waterUsage());
            assertEquals(energy, effect.energyUsage());
            if (suffix.equals("Low Temp")) assertNull(effect.control());
            else assertEquals(new ControlRequest("CH5", sensor.equals("Soil")
                ? DeviceType.WATER_PUMP : DeviceType.COOLING_FAN), effect.control());
        }
    }

    @Test
    void configuredRunRetainsItsCatalogWhileAReplacementIsCreatedConcurrently() throws Exception {
        CropCatalog original = CropExtensionDemo.catalog();
        CountDownLatch sampling = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SensorSource source = (chamber, sensor) -> {
            if (chamber.equals("CH5") && sensor == TEMPERATURE) {
                sampling.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("configuration was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("fixture interrupted");
                }
            }
            boolean kale = chamber.equals("CH5");
            return switch (sensor) {
                case TEMPERATURE -> kale ? 25 : 24;
                case HUMIDITY -> 70;
                case SOIL -> kale ? 35 : 50;
                case LIGHT -> kale ? 450 : 600;
                case NUTRIENT -> kale ? 30 : 70;
            };
        };
        Path report = directory.resolve("demo.txt");
        FarmApplication app = new FarmApplication(original, source,
            Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"), ZoneOffset.UTC), message -> {}, report, new FileArchiveWriter());
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = runner.submit(() -> { app.run(); return null; });
            assertTrue(sampling.await(5, TimeUnit.SECONDS));
            CropCatalog replacement = original.reassignChamber("CH5", "Spinach").withoutProfile("Kale");
            assertTrue(replacement.profile("Kale").isEmpty());
            assertEquals("Spinach", replacement.assignments().getLast().cropName());
            release.countDown();
            run.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            runner.shutdownNow();
            assertTrue(runner.awaitTermination(5, TimeUnit.SECONDS));
        }
        var snapshot = app.snapshot();
        assertEquals(FarmApplication.RunState.COMPLETED, app.runState());
        assertTrue(app.workersTerminated());
        assertTrue(app.pendingControls().isEmpty());
        assertEquals(500, snapshot.sensorLogs().size());
        assertEquals(100, snapshot.totalAlertsGenerated());
        assertEquals(100, snapshot.archivedAlerts().size());
        assertEquals(0, snapshot.totalAlerts());
        assertEquals(400, snapshot.totalWaterUsage());
        assertEquals(160, snapshot.totalEnergyUsage());
        var kale = snapshot.chamberSummaries().get("CH5");
        assertEquals(80, kale.alerts());
        assertEquals(80, kale.mitigationActions());
        for (String alert : List.of("CH5 Kale High Temp", "CH5 Kale Low Soil Moisture", "CH5 Low Light Level", "CH5 Low Nutrient Level")) {
            assertEquals(20, snapshot.archivedAlerts().stream().filter(alert::equals).count());
        }
        assertEquals(100, snapshot.chamberSummaries().values().stream().mapToInt(FarmState.SummarySnapshot::alerts).sum());
        assertEquals(100, snapshot.chamberSummaries().values().stream().mapToInt(FarmState.SummarySnapshot::mitigationActions).sum());
        String text = Files.readString(report);
        assertTrue(text.contains("Generated Alerts: 100"));
        assertTrue(text.contains("CH5 -> Alerts=80, Actions=80"));
        assertTrue(original.profile("Kale").isPresent());
    }
}
