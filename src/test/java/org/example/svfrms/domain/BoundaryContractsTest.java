package org.example.svfrms.domain;

import org.example.svfrms.concurrency.*;
import org.example.svfrms.domain.chamber.*;
import org.example.svfrms.domain.crop.*;
import org.example.svfrms.domain.sensor.*;
import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.example.svfrms.service.alert.AlertSnapshot;
import org.example.svfrms.service.crop.*;
import org.example.svfrms.service.report.DailyReportSnapshot;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.*;
import org.example.svfrms.service.actuator.ActuatorExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Rejected configuration/accounting boundaries, not validation of raw sensor physics. */
class BoundaryContractsTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"-1,0,0,0", "0,-1,0,0", "0,0,-1,0", "0,0,0,-1"})
    void rejectsEveryNegativeCostField(long high, long low, long humidity, long soil) {
        assertThatIllegalArgumentException().isThrownBy(() -> new MitigationResourceCosts(high,low,humidity,soil));
    }

    @ParameterizedTest
    @CsvSource({"-1,0,0,0", "0,-1,0,0", "0,0,-1,0", "0,0,0,-1"})
    void rejectsEveryNegativeReportCounter(long generated, int active, long water, long energy) {
        assertThatIllegalArgumentException().isThrownBy(() -> new DailyReportSnapshot(Instant.EPOCH,
                generated,active,water,energy,List.of(),Map.of(),List.of()));
    }

    @Test
    void rejectsNegativeAlertSummaryEvaluationAndManualResourceValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertSnapshot(-1,List.of(),List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChamberSummarySnapshot(0,-1,"none","none"));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationResult(Optional.empty(),Optional.empty(),-1,0));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationResult(Optional.empty(),Optional.empty(),0,-1));
        var resources = new ResourceAccountingService();
        assertThatIllegalArgumentException().isThrownBy(() -> resources.recordWater(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> resources.recordEnergy(-1));
        assertThat(resources.waterUsage()).isZero();
        assertThat(resources.energyUsage()).isZero();
    }

    @Test
    void rangeRejectsNaNEndpointsAndUsesInclusiveBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ThresholdRange(Double.NaN,10));
        assertThatIllegalArgumentException().isThrownBy(() -> new ThresholdRange(0,Double.NaN));
        var range = new ThresholdRange(10,20);
        assertThat(range.contains(9)).isFalse();
        assertThat(range.contains(10)).isTrue();
        assertThat(range.contains(20)).isTrue();
        assertThat(range.contains(21)).isFalse();
        assertThat(range.contains(Double.NaN)).isFalse();
    }

    @Test
    void invalidBudgetsAndIterationCountsAreRejectedBeforeStartingWorkers() throws Exception {
        for (Duration budget : List.of(Duration.ZERO,Duration.ofSeconds(-1))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new SvfrmsApplicationContext(
                    directory.resolve("report.txt"),Clock.systemUTC(),ignored -> {},42,budget));
        }
        try (var context = new SvfrmsApplicationContext(directory.resolve("report.txt"),Clock.systemUTC(),ignored -> {},42)) {
            assertThatIllegalArgumentException().isThrownBy(() -> context.runSimulation(-1));
            context.runSimulation(0);
            assertThatIllegalStateException().isThrownBy(() -> context.runSimulation(0));
        }
        var queue = new BoundedControlQueue();
        assertThatIllegalArgumentException().isThrownBy(() -> queue.offer(ActuatorRequest.stopSignal(),Duration.ofSeconds(-1)));
        try (var service = new ActuatorExecutionService(queue,new HashMap<>(),new HashMap<>(),ignored -> {})) {
            assertThatIllegalArgumentException().isThrownBy(() -> service.submit(ActuatorRequest.stopSignal(),Duration.ZERO));
            assertThatIllegalArgumentException().isThrownBy(() -> service.awaitIdle(Duration.ofSeconds(-1)));
            service.start();
            assertThatIllegalStateException().isThrownBy(service::start);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new ChamberSimulationTask(new ChamberId("CH1"),"Tomato",-1,
                new TelemetryPublisher(),new ResourceAccountingService(),new Random(42),Clock.systemUTC()));
    }

    @Test
    void strategyRejectsMismatchedProfileAndObserverRejectsNegativeEnqueueBudget() throws Exception {
        var crops = CropRegistry.withStandardProfiles();
        var reading = new SensorReading(new ChamberId("CH1"),"Kale",SensorType.TEMPERATURE,29,Instant.EPOCH);
        assertThatIllegalArgumentException().isThrownBy(() -> new DefaultCropThresholdStrategy(crops)
                .evaluate(reading,crops.get("Tomato").orElseThrow()));
        try (var service = new ActuatorExecutionService(new BoundedControlQueue(),new HashMap<>(),new HashMap<>(),ignored -> {})) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ThresholdEvaluationObserver(crops,
                    new DefaultCropThresholdStrategy(crops),new org.example.svfrms.service.alert.AlertManagementService(),
                    service,new ResourceAccountingService(),new HashMap<>(),new HashMap<>(),Duration.ofSeconds(-1)));
        }
    }
}
