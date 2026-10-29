package org.example.svfrms.domain.crop;

import java.util.Objects;

/**
 * Immutable profile storing the acceptable environmental operating ranges required for healthy crop growth.
 */
public record CropProfile(
        String name,
        ThresholdRange temperature,
        ThresholdRange humidity,
        ThresholdRange soilMoisture,
        ThresholdRange light,
        ThresholdRange nutrient
) {
    public CropProfile {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(temperature, "temperature range must not be null");
        Objects.requireNonNull(humidity, "humidity range must not be null");
        Objects.requireNonNull(soilMoisture, "soilMoisture range must not be null");
        Objects.requireNonNull(light, "light range must not be null");
        Objects.requireNonNull(nutrient, "nutrient range must not be null");
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private ThresholdRange temperature;
        private ThresholdRange humidity;
        private ThresholdRange soilMoisture;
        private ThresholdRange light;
        private ThresholdRange nutrient;

        public Builder(String name) {
            this.name = name;
        }

        public Builder temperature(double min, double max) {
            this.temperature = ThresholdRange.of(min, max);
            return this;
        }

        public Builder humidity(double min, double max) {
            this.humidity = ThresholdRange.of(min, max);
            return this;
        }

        public Builder soilMoisture(double min, double max) {
            this.soilMoisture = ThresholdRange.of(min, max);
            return this;
        }

        public Builder light(double min, double max) {
            this.light = ThresholdRange.of(min, max);
            return this;
        }

        public Builder nutrient(double min, double max) {
            this.nutrient = ThresholdRange.of(min, max);
            return this;
        }

        public CropProfile build() {
            return new CropProfile(
                    name,
                    temperature != null ? temperature : ThresholdRange.of(18.0, 26.0),
                    humidity != null ? humidity : ThresholdRange.of(60.0, 80.0),
                    soilMoisture != null ? soilMoisture : ThresholdRange.of(50.0, 70.0),
                    light != null ? light : ThresholdRange.of(500.0, 1000.0),
                    nutrient != null ? nutrient : ThresholdRange.of(40.0, 100.0)
            );
        }
    }
}
