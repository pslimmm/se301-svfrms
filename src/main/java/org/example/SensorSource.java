package org.example;

/** Supplies one sample; implementations shared across chamber tasks must be thread-safe. */
@FunctionalInterface
public interface SensorSource {
    double read(String chamber, SensorType sensor);
}
