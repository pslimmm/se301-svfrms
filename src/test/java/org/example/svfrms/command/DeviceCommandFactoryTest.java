package org.example.svfrms.command;

import org.example.svfrms.domain.actuator.ActuatorType;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberStateTracker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceCommandFactoryTest {
    @ParameterizedTest
    @CsvSource({"COOLING_FAN,19,70,60,600,70", "WATER_PUMP,24,70,80,600,70",
            "NUTRIENT_INJECTOR,24,70,60,600,85", "HUMIDIFIER,24,80,60,600,70",
            "DEHUMIDIFIER,24,60,60,600,70", "GROW_LIGHT,24,70,60,750,70"})
    void eachDevicePreservesItsActualEffect(ActuatorType type, double temperature, double humidity,
                                         double soil, double light, double nutrient) {
        var tracker = ChamberStateTracker.initial();
        var command = DeviceCommandFactory.create(type);
        assertThat(command.execute(tracker)).isEqualTo(new ChamberSnapshot(temperature, humidity, soil, light, nutrient));
        assertThat(command.type()).contains(type);
    }
}
