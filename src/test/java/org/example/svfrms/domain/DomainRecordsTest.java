package org.example.svfrms.domain;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.alert.AlertSeverity;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import org.example.svfrms.domain.sensor.SensorReading;
import org.example.svfrms.domain.sensor.SensorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Task 2: Core Domain Records & Value Objects Tests")
public class DomainRecordsTest {

    @Test
    @DisplayName("ChamberId enforces non-blank and provides consistent equality")
    void testChamberId() {
        ChamberId id1 = ChamberId.of("CH1");
        ChamberId id2 = ChamberId.of("CH1");
        ChamberId id3 = ChamberId.of("CH2");

        assertThat(id1.value()).isEqualTo("CH1");
        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1.toString()).contains("CH1");

        assertThatThrownBy(() -> ChamberId.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChamberId.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ChamberSnapshot record holds immutable telemetry state")
    void testChamberSnapshot() {
        ChamberSnapshot snapshot = new ChamberSnapshot(24.5, 70.0, 60.0, 600.0, 70.0);
        assertThat(snapshot.temperature()).isEqualTo(24.5);
        assertThat(snapshot.humidity()).isEqualTo(70.0);
        assertThat(snapshot.soilMoisture()).isEqualTo(60.0);
        assertThat(snapshot.lightLevel()).isEqualTo(600.0);
        assertThat(snapshot.nutrientLevel()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("ChamberSummarySnapshot records operational statistics")
    void testChamberSummarySnapshot() {
        ChamberSummarySnapshot summary = new ChamberSummarySnapshot(5, 3, "Cooling Fan Activated", "CH1 High Temp");
        assertThat(summary.alerts()).isEqualTo(5);
        assertThat(summary.mitigationActions()).isEqualTo(3);
        assertThat(summary.lastAction()).isEqualTo("Cooling Fan Activated");
        assertThat(summary.lastAlert()).isEqualTo("CH1 High Temp");
    }

    @Test
    @DisplayName("SensorReading record holds verified reading payload")
    void testSensorReading() {
        Instant now = Instant.now();
        SensorReading reading = new SensorReading(ChamberId.of("CH1"), "Tomato", SensorType.TEMPERATURE, 29.5, now);

        assertThat(reading.chamberId()).isEqualTo(ChamberId.of("CH1"));
        assertThat(reading.crop()).isEqualTo("Tomato");
        assertThat(reading.sensorType()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(reading.value()).isEqualTo(29.5);
        assertThat(reading.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("AlertEvent record holds alert payload and severity")
    void testAlertEvent() {
        Instant now = Instant.now();
        AlertEvent alert = new AlertEvent(ChamberId.of("CH1"), "Tomato", AlertSeverity.CRITICAL, "CH1 Tomato High Temp", now);

        assertThat(alert.chamberId()).isEqualTo(ChamberId.of("CH1"));
        assertThat(alert.crop()).isEqualTo("Tomato");
        assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.message()).isEqualTo("CH1 Tomato High Temp");
        assertThat(alert.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("ActuatorType enum contains all 6 required actuators")
    void testActuatorType() {
        assertThat(ActuatorType.values()).containsExactlyInAnyOrder(
                ActuatorType.COOLING_FAN,
                ActuatorType.WATER_PUMP,
                ActuatorType.NUTRIENT_INJECTOR,
                ActuatorType.HUMIDIFIER,
                ActuatorType.DEHUMIDIFIER,
                ActuatorType.GROW_LIGHT
        );
        assertThat(ActuatorType.COOLING_FAN.displayName()).isEqualTo("Cooling Fan");
    }
}
