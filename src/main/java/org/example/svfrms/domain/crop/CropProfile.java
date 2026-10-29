package org.example.svfrms.domain.crop;

import java.util.Objects;

/** Immutable growing conditions for a crop. */
public record CropProfile(
        String name,
        ThresholdRange temperature,
        ThresholdRange humidity,
        ThresholdRange soilMoisture,
        ThresholdRange light,
        ThresholdRange nutrient,
        MitigationResourceCosts mitigationResourceCosts) {

    public CropProfile(String name, ThresholdRange temperature, ThresholdRange humidity,
                       ThresholdRange soilMoisture, ThresholdRange light, ThresholdRange nutrient) {
        this(name, temperature, humidity, soilMoisture, light, nutrient, MitigationResourceCosts.none());
    }

    public CropProfile {
        name = new CropName(name).value();
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(humidity, "humidity");
        Objects.requireNonNull(soilMoisture, "soilMoisture");
        Objects.requireNonNull(light, "light");
        Objects.requireNonNull(nutrient, "nutrient");
        Objects.requireNonNull(mitigationResourceCosts, "mitigationResourceCosts");
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private ThresholdRange temperature;
        private ThresholdRange humidity;
        private ThresholdRange soilMoisture;
        private ThresholdRange light;
        private ThresholdRange nutrient;
        private MitigationResourceCosts mitigationResourceCosts = MitigationResourceCosts.none();

        private Builder(String name) {
            this.name = name;
        }

        public Builder temperature(ThresholdRange range) {
            temperature = range;
            return this;
        }

        public Builder humidity(ThresholdRange range) {
            humidity = range;
            return this;
        }

        public Builder soilMoisture(ThresholdRange range) {
            soilMoisture = range;
            return this;
        }

        public Builder light(ThresholdRange range) {
            light = range;
            return this;
        }

        public Builder nutrient(ThresholdRange range) {
            nutrient = range;
            return this;
        }

        public Builder mitigationResourceCosts(MitigationResourceCosts costs) {
            mitigationResourceCosts = costs;
            return this;
        }

        public CropProfile build() {
            return new CropProfile(name, temperature, humidity, soilMoisture, light, nutrient, mitigationResourceCosts);
        }
    }
}
