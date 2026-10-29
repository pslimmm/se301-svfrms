package org.example.svfrms.domain.chamber;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 4: Chamber State Tracker Tests")
public class ChamberStateTrackerTest {

    @Test
    @DisplayName("ChamberStateTracker executes atomic CAS updates")
    void testAtomicStateUpdates() {
        ChamberStateTracker tracker = new ChamberStateTracker(ChamberId.of("CH1"));
        assertThat(tracker.getSnapshot().temperature()).isEqualTo(24.0);

        // Reduce temperature by 5, clamped at min 18
        tracker.update(state -> new ChamberSnapshot(
                Math.max(18.0, state.temperature() - 5.0),
                state.humidity(),
                state.soilMoisture(),
                state.lightLevel(),
                state.nutrientLevel()
        ));

        assertThat(tracker.getSnapshot().temperature()).isEqualTo(19.0);
    }

    @Test
    @DisplayName("ChamberSummaryTracker records alerts and actions thread-safely")
    void testSummaryTrackerConcurrentUpdates() throws InterruptedException {
        ChamberSummaryTracker tracker = new ChamberSummaryTracker(ChamberId.of("CH1"));
        int threads = 8;
        int updatesPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < updatesPerThread; j++) {
                        tracker.recordAlert("Alert");
                        tracker.recordAction("Action");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        ChamberSummarySnapshot snapshot = tracker.getSnapshot();
        assertThat(snapshot.alerts()).isEqualTo(threads * updatesPerThread);
        assertThat(snapshot.mitigationActions()).isEqualTo(threads * updatesPerThread);
    }
}
