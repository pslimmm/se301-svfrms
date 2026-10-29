package org.example.svfrms.service.alert;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 7: Alert Management & Archival Pipeline Tests")
public class AlertManagementServiceTest {

    private AlertManagementService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertManagementService();
    }

    @Test
    @DisplayName("publishAlert adds alerts to active snapshot")
    void testAlertPublishingAndSnapshot() {
        AlertEvent alert1 = new AlertEvent(ChamberId.of("CH1"), "Tomato", AlertSeverity.WARNING, "CH1 Tomato High Temp", Instant.now());
        AlertEvent alert2 = new AlertEvent(ChamberId.of("CH2"), "Strawberry", AlertSeverity.WARNING, "CH2 Strawberry Low Temp", Instant.now());

        alertService.publishAlert(alert1);
        alertService.publishAlert(alert2);

        List<AlertEvent> snapshot = alertService.getActiveAlertsSnapshot();
        assertThat(snapshot).hasSize(2).containsExactly(alert1, alert2);
        assertThat(alertService.getActiveAlertsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("archiveAlerts drains active alerts atomically and updates archive store")
    void testArchiveAlertsDrainsActive() {
        AlertEvent alert1 = new AlertEvent(ChamberId.of("CH1"), "Tomato", AlertSeverity.WARNING, "CH1 Tomato High Temp", Instant.now());
        alertService.publishAlert(alert1);

        List<AlertEvent> archived = alertService.archiveAlerts();
        assertThat(archived).hasSize(1).contains(alert1);
        assertThat(alertService.getActiveAlertsSnapshot()).isEmpty();
        assertThat(alertService.getActiveAlertsCount()).isEqualTo(0);
        assertThat(alertService.getAllArchivedAlerts()).hasSize(1).contains(alert1);
    }

    @Test
    @DisplayName("Concurrent publishing, snapshotting, and archiving maintains zero lost alerts and thread safety")
    void testConcurrentPublishAndArchive() throws InterruptedException, ExecutionException {
        int producerThreads = 8;
        int alertsPerProducer = 1000;
        int totalExpectedAlerts = producerThreads * alertsPerProducer;

        ExecutorService executor = Executors.newFixedThreadPool(producerThreads + 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // Producers
        for (int i = 0; i < producerThreads; i++) {
            final int producerId = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < alertsPerProducer; j++) {
                        alertService.publishAlert(new AlertEvent(
                                ChamberId.of("CH" + (producerId % 4 + 1)),
                                "Tomato",
                                AlertSeverity.WARNING,
                                "Alert-" + producerId + "-" + j,
                                Instant.now()
                        ));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        AtomicInteger snapshotObservations = new AtomicInteger(0);
        // Concurrent snapshot reader
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                while (alertService.getTotalAlertsIngested() < totalExpectedAlerts) {
                    List<AlertEvent> snap = alertService.getActiveAlertsSnapshot();
                    snapshotObservations.addAndGet(snap.size());
                    Thread.yield();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        // Concurrent archiver
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                while (alertService.getTotalAlertsIngested() < totalExpectedAlerts) {
                    alertService.archiveAlerts();
                    Thread.sleep(2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }

        // Final archival drain
        alertService.archiveAlerts();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(alertService.getActiveAlertsCount()).isEqualTo(0);
        assertThat(alertService.getAllArchivedAlerts()).hasSize(totalExpectedAlerts);
        assertThat(alertService.getTotalAlertsIngested()).isEqualTo(totalExpectedAlerts);
    }
}
