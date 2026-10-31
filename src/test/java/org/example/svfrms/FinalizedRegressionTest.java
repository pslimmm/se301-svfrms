package org.example.svfrms;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Scenarios transferred from the four-branch audit, through the single final service graph. */
@Timeout(20)
class FinalizedRegressionTest {
    @TempDir Path directory;

    private SvfrmsApplicationContext context() {
        return new SvfrmsApplicationContext(directory.resolve("report.txt"),
                Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC), ignored -> {}, 42);
    }

    @Test
    void legacyDisabledRulesStayDisabled() {
        try (var context = context()) {
            for (String crop : new String[]{"Lettuce", "Chili"}) {
                context.processSensor("CH5", crop, "Humidity", 0);
                context.processSensor("CH5", crop, "Soil", 0);
            }
            context.processSensor("CH5", "Strawberry", "Soil", 0);
            assertThat(context.alerts().generatedAlertCount()).isZero();
            assertThat(context.resources().waterUsage()).isZero();
        }
    }

    @Test
    void unknownChambersReceiveSummaries() throws Exception {
        try (var context = context()) {
            context.processSensor("CH5", "Tomato", "Temperature", 29);
            context.awaitControls(Duration.ofSeconds(2));
            assertThat(context.getChamberSummary("CH5").alerts()).isEqualTo(1);
            assertThat(context.getChamberSummary("CH5").mitigationActions()).isEqualTo(1);
            assertThat(context.getChamberState("CH5").temperature()).isEqualTo(24);
            assertThat(context.resources().energyUsage()).isEqualTo(10);
        }
    }

    @Test
    void removedCropKeepsOnlyLegacyGlobalFallback() throws Exception {
        try (var context = context()) {
            assertThat(context.cropRegistry().unregister("Tomato")).isTrue();
            context.processSensor("CH5", "Tomato", "Temperature", 99);
            context.processSensor("CH5", "Tomato", "Light", 499);
            context.processSensor("CH5", "Tomato", "Nutrient", 39);
            context.awaitControls(Duration.ofSeconds(2));
            assertThat(context.alerts().getActiveAlertsSnapshot()).extracting(a -> a.message())
                    .containsExactly("CH5 Low Light Level", "CH5 Low Nutrient Level");
            assertThat(context.getChamberSummary("CH5").mitigationActions()).isEqualTo(2);
            assertThat(context.resources().energyUsage()).isZero();
        }
    }

    @Test
    void rawNonFiniteValuesRetainOriginalComparisons() throws Exception {
        try (var context = context()) {
            context.processSensor("CH5", "Tomato", "Temperature", Double.NaN);
            assertThat(context.alerts().generatedAlertCount()).isZero();
            context.processSensor("CH5", "Tomato", "Temperature", Double.POSITIVE_INFINITY);
            context.awaitControls(Duration.ofSeconds(2));
            context.processSensor("CH5", "Tomato", "Temperature", Double.NEGATIVE_INFINITY);
            assertThat(context.alerts().getActiveAlertsSnapshot()).extracting(a -> a.message())
                    .containsExactly("CH5 Tomato High Temp", "CH5 Tomato Low Temp");
            assertThat(context.resources().energyUsage()).isEqualTo(25);
            assertThat(context.getChamberSummary("CH5").mitigationActions()).isEqualTo(1);
        }
    }

    @Test
    void queuedControlDoesNotDisappearWhenCapacityIsExceeded() throws Exception {
        try (var context = context()) {
            for (int i = 0; i < 1100; i++) context.processSensor("CH5", "Tomato", "Temperature", 29);
            context.awaitControls(Duration.ofSeconds(5));
            assertThat(context.alerts().generatedAlertCount()).isEqualTo(1100);
            assertThat(context.getChamberSummary("CH5").mitigationActions()).isEqualTo(1100);
            assertThat(context.sensorLogs()).hasSize(1100);
            assertThat(context.resources().energyUsage()).isEqualTo(11000);
        }
    }
}
