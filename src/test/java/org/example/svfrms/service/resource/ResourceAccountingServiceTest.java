package org.example.svfrms.service.resource;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceAccountingServiceTest {
    @Test
    void parallelResourceAndAlertUpdatesAreNotLost() throws Exception {
        ResourceAccountingService accounting = new ResourceAccountingService();
        var executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Void>> updates = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) {
                updates.add(() -> {
                    for (int iteration = 0; iteration < 1_000; iteration++) {
                        accounting.recordWater(2);
                        accounting.recordEnergy(1);
                        accounting.recordAlert();
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
        assertThat(accounting.generatedAlerts()).isEqualTo(16_000);
    }

    @Test
    void preservesLegacyIrrigationAndFeedingResourceCycles() {
        ResourceAccountingService accounting = new ResourceAccountingService();

        accounting.irrigationCycle();
        accounting.feedingCycle();

        assertThat(accounting.waterUsage()).isEqualTo(100);
        assertThat(accounting.energyUsage()).isEqualTo(20);
    }
}
