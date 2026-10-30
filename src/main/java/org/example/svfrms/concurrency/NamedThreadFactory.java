package org.example.svfrms.concurrency;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Gives pool threads readable names ("chamber-1", "control-1") so thread dumps and logs make sense. */
public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable task) {
        return new Thread(task, prefix + "-" + counter.incrementAndGet());
    }
}
