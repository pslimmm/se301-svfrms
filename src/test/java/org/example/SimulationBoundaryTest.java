package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.example.ControlQueueTest.TestWorker;

import static org.junit.jupiter.api.Assertions.*;

class SimulationBoundaryTest {
    @ParameterizedTest
    @CsvSource({
        "TEMPERATURE, 30, 10, 39", "HUMIDITY, 60, 40, 99", "SOIL, 60, 20, 79",
        "LIGHT, 900, 300, 1199", "NUTRIENT, 80, 20, 99"
    })
    void randomSourceRetainsInclusiveEndpointsAndOriginalRandomBounds(
            SensorType type, int bound, double minimum, double maximum) {
        Random extremes = new Random(301) {
            private boolean first = true;
            @Override public int nextInt(int requestedBound) {
                assertEquals(bound, requestedBound);
                int result = first ? 0 : requestedBound - 1;
                first = false;
                return result;
            }
        };
        SensorSource source = new RandomSensorSource(extremes);
        assertEquals(minimum, source.read("CH1", type));
        assertEquals(maximum, source.read("CH1", type));
    }

    @Test
    void eachSampleIsProcessedBeforeTheNextAndEachCompleteIterationChargesUsage() {
        FarmState state = new FarmState();
        List<String> calls = new ArrayList<>();
        List<SensorReading> readings = new ArrayList<>();
        List<SensorType> order = List.of(SensorType.TEMPERATURE, SensorType.HUMIDITY,
            SensorType.SOIL, SensorType.LIGHT, SensorType.NUTRIENT);
        SensorSource source = (chamber, sensor) -> {
            assertEquals("CH|1", chamber);
            calls.add("read:" + sensor);
            return 42;
        };
        new ChamberSimulation("CH|1", "Fixture", source, reading -> {
            calls.add("process:" + reading.sensor());
            assertEquals("CH|1", reading.chamber());
            assertEquals("Fixture", reading.crop());
            assertEquals(42, reading.value());
            int completedIterations = readings.size() / 5;
            assertEquals(completedIterations * 2, state.snapshot().totalWaterUsage());
            readings.add(reading);
        }, state).run();
        assertEquals(100, readings.size());
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            for (SensorType sensor : order) {
                expected.add("read:" + sensor);
                expected.add("process:" + sensor);
            }
        }
        assertEquals(expected, calls);
        assertEquals(40, state.snapshot().totalWaterUsage());
        assertEquals(20, state.snapshot().totalEnergyUsage());
    }

    @Test
    void failedSamplingDoesNotChargeAnIncompleteIterationOrReadLaterSensors() {
        FarmState state = new FarmState();
        List<SensorType> observed = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("soil sensor unavailable");
        SensorSource source = (chamber, sensor) -> {
            if (sensor == SensorType.SOIL) throw failure;
            return 50;
        };
        var simulation = new ChamberSimulation("CH1", "Fixture", source,
            reading -> observed.add(reading.sensor()), state);
        assertSame(failure, assertThrows(IllegalStateException.class, simulation::run));
        assertEquals(List.of(SensorType.TEMPERATURE, SensorType.HUMIDITY), observed);
        assertEquals(0, state.snapshot().totalWaterUsage());
        assertEquals(0, state.snapshot().totalEnergyUsage());
    }

    @Test
    void interruptedSimulationStopsBeforeAnotherSampleAndPreservesTheFlag() throws Exception {
        FarmState state = new FarmState();
        List<SensorReading> readings = new ArrayList<>();
        var simulation = new ChamberSimulation("CH1", "Fixture", (chamber, sensor) -> 50,
            reading -> { readings.add(reading); Thread.currentThread().interrupt(); }, state);
        try (TestWorker<Boolean> worker = new TestWorker<>(() -> {
            try {
                simulation.run();
                return false;
            } catch (CancellationException expected) {
                return Thread.currentThread().isInterrupted();
            }
        })) {
            assertTrue(worker.result());
        }
        assertEquals(1, readings.size());
        assertEquals(0, state.snapshot().totalWaterUsage());
        assertEquals(0, state.snapshot().totalEnergyUsage());
    }
}
