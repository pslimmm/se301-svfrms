package org.example.svfrms.service.resource;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.SplittableRandom;
import java.time.*;
import org.example.svfrms.concurrency.*;
import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.crop.CropRegistry;
import org.example.svfrms.domain.sensor.*;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.example.svfrms.service.alert.AlertManagementService;
import org.example.svfrms.service.crop.DefaultCropThresholdStrategy;
import org.example.svfrms.service.telemetry.*;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceAccountingServiceTest {
    @Test
    void parallelResourceUpdatesAreNotLost() throws Exception {
        ResourceAccountingService accounting = new ResourceAccountingService();
        var executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Void>> updates = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) {
                updates.add(() -> {
                    for (int iteration = 0; iteration < 1_000; iteration++) {
                        accounting.recordWater(2);
                        accounting.recordEnergy(1);
                    }
                    return null;
                });
            }
            executor.invokeAll(updates).forEach(future -> {
                try {
                    future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        } finally {
            executor.shutdownNow();
        }

        assertThat(accounting.waterUsage()).isEqualTo(32_000);
        assertThat(accounting.energyUsage()).isEqualTo(16_000);
    }

    @Test
    void preservesLegacyIrrigationAndFeedingResourceCycles() {
        ResourceAccountingService accounting = new ResourceAccountingService();

        accounting.irrigationCycle();
        accounting.feedingCycle();

        assertThat(accounting.waterUsage()).isEqualTo(100);
        assertThat(accounting.energyUsage()).isEqualTo(20);
    }

    @Test
    void simulationAndManualCycleTotalsAreExact() throws Exception {
        ResourceAccountingService baseline = new ResourceAccountingService();
        runFourChambers(new TelemetryPublisher(), baseline);
        assertThat(baseline.waterUsage()).isEqualTo(160);
        assertThat(baseline.energyUsage()).isEqualTo(80);

        CropRegistry crops = CropRegistry.withStandardProfiles();
        var states = new LinkedHashMap<ChamberId,ChamberStateTracker>();
        var summaries = new LinkedHashMap<ChamberId,ChamberSummaryTracker>();
        for (int i = 1; i <= 4; i++) {
            ChamberId id = new ChamberId("CH" + i);
            states.put(id, new ChamberStateTracker(new ChamberSnapshot(24,70,60,600,70)));
            summaries.put(id, new ChamberSummaryTracker());
        }
        ResourceAccountingService total = new ResourceAccountingService();
        List<SensorReading> readings = new CopyOnWriteArrayList<>();
        TelemetryPublisher telemetry = new TelemetryPublisher(failure -> { throw failure; });
        telemetry.subscribe(readings::add);
        try (var actuators = new ActuatorExecutionService(new BoundedControlQueue(), states, summaries, total, ignored -> {})) {
            actuators.start();
            telemetry.subscribe(new ThresholdEvaluationObserver(crops, new DefaultCropThresholdStrategy(crops),
                    new AlertManagementService(), actuators, total, states, summaries, Duration.ofSeconds(1), ignored -> {}));
            runFourChambers(telemetry, total);
            actuators.close();
        }
        assertThat(readings).hasSize(400);
        // Calculate independently of strategy/profile data to catch accounting drift.
        long alertWater = 0;
        long alertEnergy = 0;
        for (SensorReading reading : readings) {
            boolean tomato = reading.crop().equals("Tomato");
            boolean strawberry = reading.crop().equals("Strawberry");
            if (tomato || strawberry) {
                if (reading.sensorType() == SensorType.TEMPERATURE) {
                    if (reading.value() < (tomato ? 22 : 18)) alertEnergy += 15;
                    else if (reading.value() > (tomato ? 28 : 24)) alertEnergy += 10;
                }
                if (reading.sensorType() == SensorType.HUMIDITY && reading.value() < (tomato ? 60 : 70))
                    alertWater += tomato ? 20 : 25;
            }
            if (tomato && reading.sensorType() == SensorType.SOIL_MOISTURE && reading.value() < 45) alertWater += 50;
        }
        assertThat(total.waterUsage()).isEqualTo(160 + alertWater);
        assertThat(total.energyUsage()).isEqualTo(80 + alertEnergy);
        total.irrigationCycle();
        total.feedingCycle();
        assertThat(total.waterUsage()).isEqualTo(260 + alertWater);
        assertThat(total.energyUsage()).isEqualTo(100 + alertEnergy);
    }

    private static void runFourChambers(TelemetryPublisher telemetry, ResourceAccountingService accounting) throws Exception {
        String[] names = {"Tomato", "Strawberry", "Lettuce", "Chili"};
        try (var executor = Executors.newFixedThreadPool(4)) {
            var completed = new ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < names.length; index++) {
                completed.add(executor.submit(new ChamberSimulationTask(new ChamberId("CH" + (index + 1)),
                        names[index], 20, telemetry, accounting, new SplittableRandom(42L + index), Clock.systemUTC())));
            }
            for (var future : completed) future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
