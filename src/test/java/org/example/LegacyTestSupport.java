package org.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Each test owns a fresh application; output capture no longer replaces System.out. */
abstract class LegacyTestSupport {
    protected FarmApplication app;
    protected PrintStream capturedOut;
    private ByteArrayOutputStream output;

    @BeforeEach
    void isolateApplication() {
        output = new ByteArrayOutputStream();
        capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8);
        app = new FarmApplication(new Random(301), capturedOut::println);
    }

    @AfterEach
    void closeOutput() {
        capturedOut.close();
    }

    String output() {
        return output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    void clearOutput() {
        output.reset();
    }

    double reading(String chamber, String sensor) {
        var state = app.getChamberState(chamber);
        return switch (sensor) {
            case "Temperature" -> state.temperature();
            case "Humidity" -> state.humidity();
            case "Soil" -> state.soilMoisture();
            case "Light" -> state.lightLevel();
            case "Nutrient" -> state.nutrientLevel();
            default -> throw new IllegalArgumentException(sensor);
        };
    }
}
