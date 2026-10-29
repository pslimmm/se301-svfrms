package org.example.svfrms.service.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 4: Resource Accounting Concurrency Tests")
public class ResourceAccountingServiceTest {

    @Test
    @DisplayName("Concurrent multi-threaded increments produce zero lost updates")
    void testConcurrentIncrementsNoLostUpdates() throws InterruptedException {
        ResourceAccountingService service = new ResourceAccountingService();
        int threadCount = 16;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        service.recordWaterUsage(2);
                        service.recordEnergyUsage(1);
                        service.recordAlertGenerated();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        long expectedWater = (long) threadCount * incrementsPerThread * 2;
        long expectedEnergy = (long) threadCount * incrementsPerThread * 1;
        int expectedAlerts = threadCount * incrementsPerThread;

        assertThat(service.getWaterUsageLiters()).isEqualTo(expectedWater);
        assertThat(service.getEnergyUsageKWh()).isEqualTo(expectedEnergy);
        assertThat(service.getTotalAlertsGenerated()).isEqualTo(expectedAlerts);
    }
}
