package org.example.svfrms.domain;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DomainRecordsTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    void chamberValuesAreImmutableAndCopyable() {
        ChamberSnapshot snapshot = new ChamberSnapshot(24, 70, 60, 600, 70);
        ChamberSummarySnapshot summary = new ChamberSummarySnapshot(2, 1, "Pump", "Dry soil");

        assertThat(new ChamberSnapshot(snapshot)).isEqualTo(snapshot);
        assertThat(new ChamberSummarySnapshot(summary)).isEqualTo(summary);
        assertThat(snapshot.getClass().isRecord()).isTrue();
        assertThat(snapshot.getClass().getModifiers()).matches(m -> java.lang.reflect.Modifier.isFinal(m));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChamberId("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChamberSummarySnapshot(-1, 0, "None", "None"));
    }

    @Test
    void telemetryAndAlertsRejectMissingRequiredValuesAndSupportCopies() {
        ChamberId chamber = new ChamberId("CH1");
        SensorReading reading = new SensorReading(chamber, "Tomato", SensorType.TEMPERATURE, 29, NOW);
        AlertEvent alert = new AlertEvent(chamber, "Tomato", AlertSeverity.WARNING, "High temperature", NOW);

        assertThat(new SensorReading(reading)).isEqualTo(reading);
        assertThat(new AlertEvent(alert)).isEqualTo(alert);
        assertThatNullPointerException().isThrownBy(() -> new SensorReading(null, "Tomato", SensorType.TEMPERATURE, 29, NOW));
        assertThatNullPointerException().isThrownBy(() -> new AlertEvent(chamber, "Tomato", null, "Alert", NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorReading(chamber, " ", SensorType.LIGHT, 1, NOW));
    }

    @Test
    void domainEnumsExposeSupportedDeviceAndSeverityKinds() {
        assertThat(ActuatorType.values()).contains(
                ActuatorType.COOLING_FAN,
                ActuatorType.WATER_PUMP,
                ActuatorType.NUTRIENT_INJECTOR,
                ActuatorType.HUMIDIFIER,
                ActuatorType.DEHUMIDIFIER,
                ActuatorType.GROW_LIGHT);
        assertThat(AlertSeverity.values()).contains(AlertSeverity.INFO, AlertSeverity.WARNING, AlertSeverity.CRITICAL);
        assertThat(SensorType.values()).contains(
                SensorType.TEMPERATURE,
                SensorType.HUMIDITY,
                SensorType.SOIL_MOISTURE,
                SensorType.LIGHT,
                SensorType.NUTRIENT);
    }
}
