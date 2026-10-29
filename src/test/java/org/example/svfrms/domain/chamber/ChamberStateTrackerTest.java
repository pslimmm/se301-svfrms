package org.example.svfrms.domain.chamber;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ChamberStateTrackerTest {
    @Test
    void compareAndSetUpdatesPreserveConcurrentChanges() throws Exception {
        ChamberStateTracker tracker = new ChamberStateTracker(new ChamberSnapshot(24, 70, 60, 600, 70));
        var executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Void>> updates = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) {
                updates.add(() -> {
                    for (int iteration = 0; iteration < 1_000; iteration++) {
                        tracker.updateAndGet(state -> new ChamberSnapshot(
                                state.temperature() + 1, state.humidity(), state.soilMoisture(),
                                state.lightLevel(), state.nutrientLevel()));
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

        assertThat(tracker.snapshot().temperature()).isEqualTo(16_024);
        assertThat(tracker.snapshot().humidity()).isEqualTo(70);
    }
}
