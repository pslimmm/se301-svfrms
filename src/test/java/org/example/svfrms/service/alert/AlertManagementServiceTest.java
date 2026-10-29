package org.example.svfrms.service.alert;

import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AlertManagementServiceTest {
    @Test
    @org.junit.jupiter.api.Timeout(10)
    void alertSnapshotsNeverObservePartialArchival() throws Exception {
        var service = new AlertManagementService();
        var executor = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        try {
            var producers = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int producer=0;producer<2;producer++) {
                int offset=producer*250;
                producers.add(executor.submit(() -> {
                    start.await();
                    for (int index=0;index<250;index++) service.publishAlert(alert(offset+index));
                    return null;
                }));
            }
            var archiver=executor.submit(() -> { start.await(); for (int i=0;i<250;i++) service.archiveAlerts(); return null; });
            var reader=executor.submit(() -> {
                start.await();
                for (int i=0;i<250;i++) {
                    var snapshot=service.snapshot();
                    var combined=new java.util.ArrayList<>(snapshot.activeAlerts()); combined.addAll(snapshot.archivedAlerts());
                    assertThat(snapshot.generatedAlertCount()).isEqualTo(combined.size());
                    assertThat(new HashSet<>(combined)).hasSize(combined.size());
                }
                return null;
            });
            start.countDown();
            for (var producer:producers) producer.get(2,java.util.concurrent.TimeUnit.SECONDS);
            archiver.get(2,java.util.concurrent.TimeUnit.SECONDS); reader.get(2,java.util.concurrent.TimeUnit.SECONDS);
            service.archiveAlerts();
            var expected=java.util.stream.IntStream.range(0,500).mapToObj(AlertManagementServiceTest::alert).toList();
            assertThat(service.snapshot().archivedAlerts()).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(service.snapshot().activeAlerts()).isEmpty();
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }
    @Test
    void snapshotsStayImmutableWhileArchiveDrainsTheActiveQueue() throws Exception {
        AlertManagementService service = new AlertManagementService();
        for (int index = 0; index < 1_000; index++) {
            service.publishAlert(alert(index));
        }
        List<AlertEvent> beforeArchive = service.getActiveAlertsSnapshot();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var archive = executor.submit(() -> {
                start.await();
                return service.archiveAlerts();
            });
            var snapshots = executor.submit(() -> {
                start.await();
                for (int i = 0; i < 100; i++) {
                    List<AlertEvent> current = service.getActiveAlertsSnapshot();
                    assertThat(new HashSet<>(current)).hasSize(current.size());
                }
                return true;
            });
            start.countDown();

            List<AlertEvent> archived = archive.get();
            assertThat(snapshots.get()).isTrue();
            assertThat(archived).hasSize(1_000);
            assertThat(beforeArchive).hasSize(1_000);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> beforeArchive.add(alert(1_001)));
            assertThat(service.getActiveAlertsSnapshot()).isEmpty();
            assertThat(service.getArchivedAlertsSnapshot()).containsExactlyElementsOf(archived);
            assertThat(service.generatedAlertCount()).isEqualTo(1_000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentPublishAndArchivePreserveEveryAlert() throws Exception {
        AlertManagementService service = new AlertManagementService();
        var executor = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var producers = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int producer = 0; producer < 8; producer++) {
                int producerId = producer;
                producers.add(executor.submit(() -> {
                    start.await();
                    for (int item = 0; item < 100; item++) {
                        service.publishAlert(alert(producerId * 100 + item));
                    }
                    return null;
                }));
            }
            var archiver = executor.submit(() -> {
                start.await();
                for (int i = 0; i < 100; i++) {
                    service.archiveAlerts();
                    Thread.yield();
                }
                return null;
            });
            start.countDown();
            for (var producer : producers) producer.get();
            archiver.get();
            service.archiveAlerts();

            List<AlertEvent> archived = service.getArchivedAlertsSnapshot();
            assertThat(archived).hasSize(800);
            assertThat(new HashSet<>(archived)).hasSize(800);
            assertThat(service.getActiveAlertsSnapshot()).isEmpty();
            assertThat(service.generatedAlertCount()).isEqualTo(800);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void combinedSnapshotAlwaysAccountsForEachPublishedAlertAcrossArchival() throws Exception {
        AlertManagementService service = new AlertManagementService();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var publisher = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 2_000; index++) {
                    service.publishAlert(alert(index));
                    if (index % 5 == 0) service.archiveAlerts();
                }
                return null;
            });
            var observer = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 2_000; index++) {
                    var snapshot = service.snapshot();
                    assertThat(snapshot.generatedAlertCount()).isEqualTo(
                            snapshot.activeAlerts().size() + snapshot.archivedAlerts().size());
                }
                return null;
            });
            start.countDown();
            publisher.get();
            observer.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static AlertEvent alert(int id) {
        return new AlertEvent(new ChamberId("CH1"), "Tomato", AlertSeverity.WARNING,
                "Alert " + id, Instant.ofEpochMilli(id));
    }
}
