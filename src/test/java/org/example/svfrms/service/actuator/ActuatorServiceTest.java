package org.example.svfrms.service.actuator;

import org.example.svfrms.command.*;
import org.example.svfrms.concurrency.BoundedControlQueue;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.example.svfrms.domain.chamber.ChamberSummaryTracker;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 5: Actuator Command Pattern & Execution Service Tests")
public class ActuatorServiceTest {

    private ChamberId chamberId;
    private ChamberStateTracker stateTracker;
    private ChamberSummaryTracker summaryTracker;
    private ResourceAccountingService metrics;
    private BoundedControlQueue queue;

    @BeforeEach
    void setUp() {
        chamberId = ChamberId.of("CH1");
        stateTracker = new ChamberStateTracker(chamberId, new ChamberSnapshot(25.0, 70.0, 50.0, 500.0, 50.0));
        summaryTracker = new ChamberSummaryTracker(chamberId);
        metrics = new ResourceAccountingService();
        queue = new BoundedControlQueue(100);
    }

    @Test
    @DisplayName("CoolingFanCommand reduces temperature by 5, clamped at min 18")
    void testCoolingFanCommand() {
        DeviceCommand cmd = new CoolingFanCommand(chamberId);
        cmd.execute(stateTracker, summaryTracker, metrics);

        assertThat(stateTracker.getSnapshot().temperature()).isEqualTo(20.0);
        assertThat(summaryTracker.getSnapshot().mitigationActions()).isEqualTo(1);
        assertThat(summaryTracker.getSnapshot().lastAction()).isEqualTo("Cooling Fan Activated");

        // Second execution reaches clamp at 18
        cmd.execute(stateTracker, summaryTracker, metrics);
        assertThat(stateTracker.getSnapshot().temperature()).isEqualTo(18.0);
    }

    @Test
    @DisplayName("WaterPumpCommand increases soil moisture by 20, clamped at max 100")
    void testWaterPumpCommand() {
        DeviceCommand cmd = new WaterPumpCommand(chamberId);
        cmd.execute(stateTracker, summaryTracker, metrics);

        assertThat(stateTracker.getSnapshot().soilMoisture()).isEqualTo(70.0);
        assertThat(summaryTracker.getSnapshot().lastAction()).isEqualTo("Water Pump Activated");
    }

    @Test
    @DisplayName("ActuatorExecutionService consumes commands and stops cleanly via poison pill")
    void testActuatorExecutionServiceLifecycle() throws InterruptedException {
        Map<ChamberId, ChamberStateTracker> stateMap = new ConcurrentHashMap<>();
        Map<ChamberId, ChamberSummaryTracker> summaryMap = new ConcurrentHashMap<>();
        stateMap.put(chamberId, stateTracker);
        summaryMap.put(chamberId, summaryTracker);

        ActuatorExecutionService service = new ActuatorExecutionService(queue, stateMap, summaryMap, metrics);
        service.start();

        queue.offer(new CoolingFanCommand(chamberId));
        queue.offer(new WaterPumpCommand(chamberId));
        queue.offer(new PoisonPillCommand());

        boolean finished = service.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(stateTracker.getSnapshot().temperature()).isEqualTo(20.0);
        assertThat(stateTracker.getSnapshot().soilMoisture()).isEqualTo(70.0);
        assertThat(summaryTracker.getSnapshot().mitigationActions()).isEqualTo(2);
    }
}
