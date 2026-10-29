package org.example.svfrms;

import org.example.svfrms.command.*;
import org.example.svfrms.concurrency.*;
import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.crop.*;
import org.example.svfrms.domain.sensor.*;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.*;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class LegacyCompatibilityTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final ChamberId CHAMBER = new ChamberId("CH1");

    record Rule(String crop, SensorType sensor, double threshold, boolean low,
                String message, ActuatorType actuator, long water, long energy) { }
    record Boundary(Rule rule, double value) { }

    static Stream<Boundary> matrix() {
        List<Rule> rules = new ArrayList<>();
        String[] crops = {"Tomato", "Strawberry", "Lettuce", "Spinach", "Chili", "Cucumber"};
        int[][] temps = {{22,28},{18,24},{15,21},{10,20},{24,32},{18,27}};
        for (int i = 0; i < crops.length; i++) {
            String crop = crops[i];
            rules.add(new Rule(crop, SensorType.TEMPERATURE, temps[i][0], true,
                    "CH1 " + crop + " Low Temp", null, 0, i < 2 ? 15 : 0));
            rules.add(new Rule(crop, SensorType.TEMPERATURE, temps[i][1], false,
                    "CH1 " + crop + " High Temp", ActuatorType.COOLING_FAN, 0, i < 2 ? 10 : 0));
            rules.add(new Rule(crop, SensorType.LIGHT, 500, true,
                    "CH1 Low Light Level", ActuatorType.GROW_LIGHT, 0, 0));
            rules.add(new Rule(crop, SensorType.NUTRIENT, 40, true,
                    "CH1 Low Nutrient Level", ActuatorType.NUTRIENT_INJECTOR, 0, 0));
        }
        rules.add(new Rule("Tomato", SensorType.HUMIDITY, 60, true, "CH1 Tomato Low Humidity", ActuatorType.HUMIDIFIER, 20, 0));
        rules.add(new Rule("Tomato", SensorType.HUMIDITY, 80, false, "CH1 Tomato High Humidity", ActuatorType.DEHUMIDIFIER, 0, 0));
        rules.add(new Rule("Strawberry", SensorType.HUMIDITY, 70, true, "CH1 Strawberry Low Humidity", ActuatorType.HUMIDIFIER, 25, 0));
        rules.add(new Rule("Strawberry", SensorType.HUMIDITY, 85, false, "CH1 Strawberry High Humidity", ActuatorType.DEHUMIDIFIER, 0, 0));
        rules.add(new Rule("Tomato", SensorType.SOIL_MOISTURE, 45, true, "CH1 Tomato Low Soil Moisture", ActuatorType.WATER_PUMP, 50, 0));
        for (String crop : List.of("Unknown", "tomato", "")) {
            rules.add(new Rule(crop, SensorType.LIGHT, 500, true, "CH1 Low Light Level", ActuatorType.GROW_LIGHT, 0, 0));
            rules.add(new Rule(crop, SensorType.NUTRIENT, 40, true, "CH1 Low Nutrient Level", ActuatorType.NUTRIENT_INJECTOR, 0, 0));
        }
        return rules.stream().flatMap(rule -> Stream.of(Math.nextDown(rule.threshold()), rule.threshold(), Math.nextUp(rule.threshold()))
                .map(value -> new Boundary(rule, value)));
    }

    @ParameterizedTest
    @MethodSource("matrix")
    void legacyMatrixMatchesReference(Boundary boundary) {
        Rule rule = boundary.rule();
        CropRegistry crops = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(crops);
        SensorReading reading = reading(rule.crop(), rule.sensor(), boundary.value());
        EvaluationResult result = crops.get(rule.crop()).map(profile -> strategy.evaluate(reading, profile))
                .orElseGet(() -> strategy.evaluateWithoutProfile(reading));
        boolean triggered = rule.low() ? boundary.value() < rule.threshold() : boundary.value() > rule.threshold();
        if (triggered) {
            assertThat(result.alert()).get().satisfies(alert -> {
                assertThat(alert.message()).isEqualTo(rule.message());
                assertThat(alert.crop()).isEqualTo(rule.crop());
                assertThat(alert.timestamp()).isEqualTo(NOW);
            });
            assertThat(result.mitigation()).isEqualTo(Optional.ofNullable(rule.actuator()));
            assertThat(result.waterUsage()).isEqualTo(rule.water());
            assertThat(result.energyUsage()).isEqualTo(rule.energy());
        } else {
            assertThat(result).isEqualTo(EvaluationResult.none());
        }
    }

    @Test
    void disabledSensorsOnlyUpdateStateAndLog() throws Exception {
        try (Rig rig = new Rig()) {
            rig.publish("Lettuce", SensorType.HUMIDITY, -1);
            rig.publish("Strawberry", SensorType.SOIL_MOISTURE, -1);
            assertThat(rig.state.snapshot().humidity()).isEqualTo(-1);
            assertThat(rig.state.snapshot().soilMoisture()).isEqualTo(-1);
            assertThat(rig.logs).hasSize(2);
            assertThat(rig.logs.get(0)).endsWith(" : Processing Lettuce Humidity = -1.0");
            assertThat(rig.logs.get(1)).endsWith(" : Processing Strawberry Soil = -1.0");
            assertThat(rig.alerts.generatedAlertCount()).isZero();
            assertThat(rig.summary.snapshot().mitigationActions()).isZero();
            assertThat(rig.metrics.waterUsage()).isZero();
            assertThat(rig.metrics.energyUsage()).isZero();
        }
    }

    @Test
    void unknownAndCaseMismatchedCropsKeepUniversalChecks() throws Exception {
        try (Rig rig = new Rig()) {
            for (String crop : List.of("Unknown", "tomato", " Tomato", "Tomato ", "", " ")) {
                rig.publish(crop, SensorType.TEMPERATURE, 100);
                rig.publish(crop, SensorType.LIGHT, 499);
                rig.publish(crop, SensorType.NUTRIENT, 39);
            }
            rig.actuators.close();
            assertThat(rig.alerts.generatedAlertCount()).isEqualTo(12);
            assertThat(rig.summary.snapshot().mitigationActions()).isEqualTo(12);
            assertThat(rig.metrics.energyUsage()).isZero();
            assertThat(rig.alerts.getActiveAlertsSnapshot()).allSatisfy(alert ->
                    assertThat(alert.message()).isIn("CH1 Low Light Level", "CH1 Low Nutrient Level"));
        }
    }

    @Test
    void chargesExistBeforeConsumerRuns() throws Exception {
        try (Rig rig = new Rig()) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            DeviceCommand blocker = new AbstractDeviceCommand(ActuatorType.GROW_LIGHT, "Barrier") {
                public ChamberSnapshot execute(ChamberStateTracker tracker) {
                    entered.countDown();
                    await(release);
                    return tracker.snapshot();
                }
                public String stateDescription(ChamberSnapshot state) { return "Barrier"; }
            };
            rig.actuators.submit(new ActuatorRequest(CHAMBER, blocker), Duration.ofSeconds(1));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                rig.publish("Tomato", SensorType.TEMPERATURE, 29);
                assertThat(rig.metrics.energyUsage()).isEqualTo(10);
                assertThat(rig.state.snapshot().temperature()).isEqualTo(29);
                assertThat(rig.summary.snapshot().mitigationActions()).isZero();
            } finally { release.countDown(); }
            rig.actuators.close();
            assertThat(rig.state.snapshot().temperature()).isEqualTo(24);
            assertThat(rig.metrics.energyUsage()).isEqualTo(10);
        }
        try (Rig rig = new Rig()) {
            rig.publish("Tomato", SensorType.TEMPERATURE, 21);
            assertThat(rig.metrics.energyUsage()).isEqualTo(15);
            assertThat(rig.summary.snapshot().mitigationActions()).isZero();
            new CoolingFanCommand().execute(rig.state);
            new WaterPumpCommand().execute(rig.state);
            new HumidifierCommand().execute(rig.state);
            assertThat(rig.metrics.energyUsage()).isEqualTo(15);
            assertThat(rig.metrics.waterUsage()).isZero();
        }
    }

    @Test
    void submissionFailureRetainsChargeAndAlertWithoutSuccessfulMitigation() throws Exception {
        try (Rig rig = new Rig()) {
            rig.actuators.close();
            assertThatIllegalStateException().isThrownBy(() -> rig.publish("Tomato", SensorType.TEMPERATURE, 29));
            assertThat(rig.alerts.generatedAlertCount()).isEqualTo(1);
            assertThat(rig.metrics.energyUsage()).isEqualTo(10);
            assertThat(rig.summary.snapshot().mitigationActions()).isZero();
        }
    }

    @Test
    void exceptionalNumericInputsRetainRawComparisonBehavior() {
        CropRegistry crops = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(crops);
        for (SensorType sensor : SensorType.values()) {
            assertThat(strategy.evaluate(reading("Tomato", sensor, Double.NaN), crops.get("Tomato").orElseThrow()))
                    .isEqualTo(EvaluationResult.none());
        }
        assertThat(strategy.evaluate(reading("Lettuce", SensorType.HUMIDITY, Double.NEGATIVE_INFINITY), crops.get("Lettuce").orElseThrow()))
                .isEqualTo(EvaluationResult.none());
        assertThat(strategy.evaluate(reading("Tomato", SensorType.TEMPERATURE, Double.POSITIVE_INFINITY), crops.get("Tomato").orElseThrow()).energyUsage()).isEqualTo(10);
        assertThat(strategy.evaluate(reading("Tomato", SensorType.TEMPERATURE, Double.NEGATIVE_INFINITY), crops.get("Tomato").orElseThrow()).energyUsage()).isEqualTo(15);
        assertThat(strategy.evaluate(reading("Tomato", SensorType.LIGHT, Double.POSITIVE_INFINITY), crops.get("Tomato").orElseThrow())).isEqualTo(EvaluationResult.none());
    }

    @Test
    void everyDisabledLegacyBranchIgnoresExceptionalValues() {
        CropRegistry crops = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(crops);
        for (String crop : List.of("Tomato", "Strawberry", "Lettuce", "Spinach", "Chili", "Cucumber")) {
            for (SensorType sensor : List.of(SensorType.HUMIDITY, SensorType.SOIL_MOISTURE)) {
                if (sensor == SensorType.HUMIDITY && (crop.equals("Tomato") || crop.equals("Strawberry"))) continue;
                if (sensor == SensorType.SOIL_MOISTURE && crop.equals("Tomato")) continue;
                for (double value : new double[]{-1, Double.NEGATIVE_INFINITY, Double.NaN, Double.POSITIVE_INFINITY}) {
                    assertThat(strategy.evaluate(reading(crop,sensor,value), crops.get(crop).orElseThrow()))
                            .as("%s %s=%s", crop, sensor, value).isEqualTo(EvaluationResult.none());
                }
            }
        }
    }

    @Test
    void frozenOriginalReadingAlertsActionsAndChargesMatchEvaluation() throws Exception {
        // Task 1 separately proves these immutable observations against the isolated original binary.
        var observations = new LinkedHashMap<String,List<String[]>>();
        LegacyReferenceHarness.fixture().observations().lines().map(line -> line.split("\t", -1))
                .forEach(fields -> observations.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(fields));
        var actions = Map.of(ActuatorType.COOLING_FAN,"Cooling Fan Activated", ActuatorType.WATER_PUMP,"Water Pump Activated",
                ActuatorType.HUMIDIFIER,"Humidifier Activated", ActuatorType.DEHUMIDIFIER,"Dehumidifier Activated",
                ActuatorType.GROW_LIGHT,"Grow Light Activated", ActuatorType.NUTRIENT_INJECTOR,"Nutrient Injector Activated");
        CropRegistry crops = CropRegistry.withStandardProfiles();
        DefaultCropThresholdStrategy strategy = new DefaultCropThresholdStrategy(crops);
        long water = 0, energy = 0, generated = 0;
        Map<String,Long> mitigationCounts = new HashMap<>();
        int verified = 0;
        for (List<String[]> step : observations.values()) {
            String[] operation = step.getFirst();
            String[] counters = step.stream().filter(fields -> fields[1].equals("COUNTERS")).findFirst().orElseThrow();
            long nextWater = Long.parseLong(counters[2]);
            long nextEnergy = Long.parseLong(counters[3]);
            long nextGenerated = Long.parseLong(counters[5]);
            if (operation[2].equals("READ")) {
                SensorType sensor = switch (operation[5]) {
                    case "Temperature" -> SensorType.TEMPERATURE;
                    case "Humidity" -> SensorType.HUMIDITY;
                    case "Soil" -> SensorType.SOIL_MOISTURE;
                    case "Light" -> SensorType.LIGHT;
                    case "Nutrient" -> SensorType.NUTRIENT;
                    default -> null; // The raw unknown sensor workflow belongs to the compatibility facade.
                };
                if (sensor != null) {
                    SensorReading reading = new SensorReading(new ChamberId(operation[3]), operation[4], sensor,
                            Double.parseDouble(operation[6]), NOW);
                    EvaluationResult result = crops.get(reading.crop()).map(profile -> strategy.evaluate(reading,profile))
                            .orElseGet(() -> strategy.evaluateWithoutProfile(reading));
                    assertThat(result.waterUsage()).as(Arrays.toString(operation)).isEqualTo(nextWater - water);
                    assertThat(result.energyUsage()).as(Arrays.toString(operation)).isEqualTo(nextEnergy - energy);
                    assertThat(result.alert().isPresent()).as(Arrays.toString(operation)).isEqualTo(nextGenerated > generated);
                    if (result.alert().isPresent()) {
                        var active = step.stream().filter(fields -> fields[1].equals("ACTIVE")).toList();
                        assertThat(result.alert().orElseThrow().message()).isEqualTo(active.getLast()[2]);
                    }
                    String[] summary = step.stream().filter(fields -> fields[1].equals("SUMMARY") && fields[2].equals(operation[3]))
                            .findFirst().orElse(null);
                    long nextActions = summary == null ? 0 : Long.parseLong(summary[4]);
                    boolean commandExecuted = nextActions > mitigationCounts.getOrDefault(operation[3],0L);
                    assertThat(result.mitigation().isPresent()).as(Arrays.toString(operation)).isEqualTo(commandExecuted);
                    if (commandExecuted) assertThat(actions.get(result.mitigation().orElseThrow())).isEqualTo(summary[5]);
                    verified++;
                }
            }
            water = nextWater;
            energy = nextEnergy;
            generated = nextGenerated;
            for (String[] row : step) if (row[1].equals("SUMMARY")) mitigationCounts.put(row[2],Long.parseLong(row[4]));
        }
        assertThat(verified).isGreaterThan(100);
    }

    private static SensorReading reading(String crop, SensorType sensor, double value) {
        return new SensorReading(CHAMBER, crop, sensor, value, NOW);
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Barrier timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    static class Rig implements AutoCloseable {
        final CropRegistry crops = CropRegistry.withStandardProfiles();
        final ChamberStateTracker state = new ChamberStateTracker(new ChamberSnapshot(24,70,60,600,70));
        final ChamberSummaryTracker summary = new ChamberSummaryTracker();
        final ResourceAccountingService metrics = new ResourceAccountingService();
        final AlertManagementService alerts = new AlertManagementService();
        final List<String> logs = new CopyOnWriteArrayList<>();
        final ActuatorExecutionService actuators = new ActuatorExecutionService(new BoundedControlQueue(),
                Map.of(CHAMBER, state), Map.of(CHAMBER, summary), metrics, ignored -> {});
        final TelemetryPublisher telemetry = new TelemetryPublisher(failure -> { throw failure; });

        Rig() {
            actuators.start();
            telemetry.subscribe(new ConsoleLoggingObserver(logs::add, Clock.fixed(NOW, ZoneOffset.UTC)));
            telemetry.subscribe(new ThresholdEvaluationObserver(crops, new DefaultCropThresholdStrategy(crops), alerts,
                    actuators, metrics, Map.of(CHAMBER, state), Map.of(CHAMBER, summary), Duration.ofSeconds(1), ignored -> {}));
        }
        void publish(String crop, SensorType sensor, double value) { telemetry.publish(reading(crop, sensor, value)); }
        public void close() throws Exception { actuators.close(); }
    }
}


