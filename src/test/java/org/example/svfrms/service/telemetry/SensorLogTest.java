package org.example.svfrms.service.telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class SensorLogTest {
    @Test
    void snapshotsPreserveOrderAreImmutableAndDoNotChangeAfterAppend() {
        var log = new SensorLog();
        log.append("first");
        var before = log.snapshot();
        log.append("second");
        assertThat(before).containsExactly("first");
        assertThat(log.snapshot()).containsExactly("first", "second");
        assertThatThrownBy(() -> before.add("corruption")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void concurrentWritersRetainEveryUniqueEntry() throws Exception {
        var log = new SensorLog();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int thread = 0; thread < 4; thread++) {
                int writer = thread;
                tasks.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1000; i++) log.append(writer + ":" + i);
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
        }
        assertThat(log.snapshot()).hasSize(4000).doesNotHaveDuplicates();
        for (int writer = 0; writer < 4; writer++) {
            String prefix = writer + ":";
            assertThat(log.snapshot().stream().filter(s -> s.startsWith(prefix))).containsExactlyElementsOf(
                    java.util.stream.IntStream.range(0,1000).mapToObj(i -> prefix+i).toList());
        }
    }
}
