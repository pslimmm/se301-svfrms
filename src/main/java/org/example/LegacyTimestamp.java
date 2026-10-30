package org.example;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Date.toString-style text, with an explicit clock and zone for reproducible output. */
final class LegacyTimestamp {
    private final Clock clock;
    private final DateTimeFormatter formatter;

    LegacyTimestamp(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
            .withZone(clock.getZone());
    }

    String now() {
        return formatter.format(clock.instant());
    }
}
