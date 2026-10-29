package org.example.svfrms.domain.chamber;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ChamberStateTrackerTest {
    @Test
    void sameChamberHasOneTrackerUnderConcurrentCreation() throws Exception {
        try (var context = new org.example.svfrms.infrastructure.context.SvfrmsApplicationContext(
                java.nio.file.Path.of("target/task3-report.txt"), java.time.Clock.systemUTC(), ignored -> {}, 42);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<ChamberSnapshot>>();
            for (int i=0;i<100;i++) futures.add(executor.submit(() -> { start.await(); return context.getChamberState("NEW"); }));
            start.countDown();
            var first = futures.getFirst().get();
            // Every new tracker owns its own initial snapshot; identity exposes split initialization.
            for (var future : futures) assertThat(future.get()).isSameAs(first);
            assertThat(context.chamberStates()).hasSize(1);
            assertThat(context.chamberSummaries()).isEmpty();
        }
    }

    @Test
    void sensorUpdatesPreserveConcurrentControlFields() throws Exception {
        try (var context = new org.example.svfrms.infrastructure.context.SvfrmsApplicationContext(
                java.nio.file.Path.of("target/task3-report.txt"), java.time.Clock.systemUTC(), ignored -> {}, 42);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var readings = executor.submit(() -> { start.await(); for (int i=0;i<1000;i++) context.processSensor("NEW","Unknown","Temperature",i); return null; });
            var controls = executor.submit(() -> { start.await(); for (int i=0;i<100;i++) context.activateControl("NEW","Water Pump"); return null; });
            start.countDown(); readings.get(); controls.get();
            assertThat(context.getChamberState("NEW")).isEqualTo(new ChamberSnapshot(999,70,100,600,70));
            assertThat(context.getChamberSummary("NEW").mitigationActions()).isEqualTo(100);
        }
    }

    @Test
    void retryableStateUpdatesDoNotRepeatSideEffects() throws Exception {
        var tracker = ChamberStateTracker.initial();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        var output = new java.util.concurrent.CopyOnWriteArrayList<ChamberSnapshot>();
        var summary = new ChamberSummaryTracker();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> operation = () -> {
                var first = new java.util.concurrent.atomic.AtomicBoolean(true);
                var committed = tracker.updateAndGet(s -> {
                    callbacks.incrementAndGet();
                    if (first.getAndSet(false)) try { barrier.await(2,java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { throw new AssertionError(e); }
                    return new ChamberSnapshot(s.temperature()+1,s.humidity(),s.soilMoisture(),s.lightLevel(),s.nutrientLevel());
                });
                summary.recordMitigationAction("completed"); output.add(committed); return null;
            };
            for (var future : executor.invokeAll(List.of(operation,operation))) future.get();
        }
        assertThat(callbacks.get()).isGreaterThan(2);
        assertThat(tracker.snapshot().temperature()).isEqualTo(26);
        assertThat(summary.snapshot().mitigationActions()).isEqualTo(2);
        assertThat(output).hasSize(2);
    }

    @Test
    void summaryCountsAndTheirLastMessagesAreOneAtomicSnapshot() throws Exception {
        var summary = new ChamberSummaryTracker();
        var done = new java.util.concurrent.atomic.AtomicBoolean();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> { for (int i=1;i<=100000;i++) { summary.recordAlert(""+i); summary.recordMitigationAction(""+i); } done.set(true); });
            var reader = executor.submit(() -> {
                while (!done.get()) {
                    var s=summary.snapshot();
                    if (s.alerts()>0) assertThat(s.lastAlert()).isEqualTo(""+s.alerts());
                    if (s.mitigationActions()>0) assertThat(s.lastAction()).isEqualTo(""+s.mitigationActions());
                }
            });
            writer.get(); reader.get();
        }
        assertThat(summary.snapshot()).isEqualTo(new ChamberSummarySnapshot(100000,100000,"100000","100000"));
    }
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
