package org.example.svfrms.concurrency;

import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.sensor.SensorType;
import org.example.svfrms.service.resource.ResourceAccountingService;
import org.example.svfrms.service.telemetry.TelemetryPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class ChamberSimulationTaskTest {
    @Test
    @Timeout(5)
    void interruptedPartialIterationStopsAndCannotReportSuccessfulFuture() throws Exception {
        var readings = new CopyOnWriteArrayList<SensorType>();
        var publisher = new TelemetryPublisher(failure -> { throw failure; });
        publisher.subscribe(reading -> {
            readings.add(reading.sensorType());
            Thread.currentThread().interrupt();
        });
        var resources = new ResourceAccountingService();
        var task = new ChamberSimulationTask(new ChamberId("CH1"), "Tomato", 20,
                publisher, resources, new SplittableRandom(42), Clock.systemUTC());
        var failure = new AtomicReference<Throwable>();
        var interrupted = new AtomicBoolean();
        Thread producer = Thread.ofVirtual().start(() -> {
            try { task.run(); } catch (Throwable e) { failure.set(e); }
            finally { interrupted.set(Thread.currentThread().isInterrupted()); }
        });
        try {
            producer.join(2000);
            assertThat(producer.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(CancellationException.class);
            assertThat(interrupted).isTrue();
            assertThat(readings).containsExactly(SensorType.TEMPERATURE);
            assertThat(resources.waterUsage()).isZero();
            assertThat(resources.energyUsage()).isZero();
        } finally { producer.interrupt(); producer.join(2000); }
    }
}
