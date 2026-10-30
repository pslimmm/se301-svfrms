package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ControlQueueTest {
    @Test
    void closeEmptyQueueIsIdempotentAndRejectsLateSubmission() throws Exception {
        ControlQueue queue = new ControlQueue(5);
        queue.close();
        queue.close();
        assertNull(queue.take());
        assertThrows(RejectedExecutionException.class, () -> queue.submit(request(1)));
        assertTrue(queue.snapshot().isEmpty());
    }

    @Test
    void closeWakesAnIdleConsumer() throws Exception {
        ControlQueue queue = new ControlQueue(5);
        try (TestWorker<ControlRequest> consumer = new TestWorker<>(queue::take)) {
            consumer.awaitState(Thread.State.TIMED_WAITING);
            queue.close();
            assertNull(consumer.result());
        }
    }

    @Test
    void gracefulCloseRetainsAnAdmittedBlockedProducerAndDrainsInFifoOrder() throws Exception {
        ControlQueue queue = new ControlQueue(5);
        for (int i = 0; i < 5; i++) queue.submit(request(i));
        try (TestWorker<Void> producer = new TestWorker<>(() -> { queue.submit(request(5)); return null; })) {
            producer.awaitState(Thread.State.WAITING);
            assertEquals(5, queue.snapshot().size());
            queue.close();
            assertThrows(RejectedExecutionException.class, () -> queue.submit(request(6)));
            for (int i = 0; i < 6; i++) assertEquals(request(i), queue.take());
            producer.result();
            assertNull(queue.take());
            assertTrue(queue.snapshot().isEmpty());
        }
    }

    @Test
    void abortInterruptsBlockedSendersAndPreservesTheFirstFailure() throws Exception {
        ControlQueue queue = new ControlQueue(5);
        for (int i = 0; i < 5; i++) queue.submit(request(i));
        IllegalStateException original = new IllegalStateException("consumer failed");
        try (TestWorker<Boolean> producer = new TestWorker<>(() -> {
            try {
                queue.submit(request(5));
                return false;
            } catch (InterruptedException expected) {
                return true;
            }
        })) {
            producer.awaitState(Thread.State.WAITING);
            queue.abort(original);
            queue.abort(new CancellationException("secondary cancellation"));
            assertTrue(producer.result());
            assertSame(original, queue.failure());
            assertThrows(CancellationException.class, queue::take);
            assertThrows(RejectedExecutionException.class, () -> queue.submit(request(6)));
            assertEquals(5, queue.snapshot().size(), "unexecuted requests remain inspectable after failure");
        }
    }

    @Test
    void interruptedSubmissionDoesNotLeakAdmissionAndPreventDrain() throws Exception {
        ControlQueue queue = new ControlQueue(5);
        for (int i = 0; i < 5; i++) queue.submit(request(i));
        try (TestWorker<Void> producer = new TestWorker<>(() -> { queue.submit(request(5)); return null; })) {
            producer.awaitState(Thread.State.WAITING);
            producer.thread.interrupt();
            assertInstanceOf(InterruptedException.class, assertThrows(ExecutionException.class, producer::result).getCause());
            queue.close();
            for (int i = 0; i < 5; i++) assertEquals(request(i), queue.take());
            assertNull(queue.take());
        }
    }

    @Test
    void successfulConcurrentSubmissionsAreConsumedExactlyOnce() throws Exception {
        ControlQueue queue = new ControlQueue(5);
        ExecutorService producers = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try (TestWorker<List<ControlRequest>> consumer = new TestWorker<>(() -> {
            List<ControlRequest> executed = new ArrayList<>();
            ControlRequest next;
            while ((next = queue.take()) != null) executed.add(next);
            return executed;
        })) {
            List<Future<?>> sends = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                int id = worker;
                sends.add(producers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 25; i++) queue.submit(request(id * 25 + i));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> send : sends) send.get(5, TimeUnit.SECONDS);
            queue.close();
            List<ControlRequest> executed = consumer.result();
            assertEquals(100, executed.size());
            assertEquals(100, executed.stream().distinct().count());
            for (int i = 0; i < 100; i++) assertTrue(executed.contains(request(i)));
        } finally {
            start.countDown();
            queue.abort(new CancellationException("test cleanup"));
            producers.shutdownNow();
            assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static ControlRequest request(int id) {
        return new ControlRequest("CH" + id, DeviceType.WATER_PUMP);
    }

    static final class TestWorker<T> implements AutoCloseable {
        final Thread thread;
        private final FutureTask<T> task;

        TestWorker(Callable<T> action) {
            task = new FutureTask<>(action);
            thread = new Thread(task, "lifecycle-test-worker");
            thread.setDaemon(true);
            thread.start();
        }

        T result() throws Exception {
            return task.get(8, TimeUnit.SECONDS);
        }

        void awaitState(Thread.State expected) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (thread.isAlive() && thread.getState() != expected && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertEquals(expected, thread.getState());
        }

        @Override public void close() throws InterruptedException {
            thread.interrupt();
            thread.join(5000);
            assertFalse(thread.isAlive(), "test worker must terminate");
        }
    }
}
