package org.example.svfrms.crop;

import java.util.Objects;

import org.example.svfrms.model.Breach;
import org.example.svfrms.model.Range;
import org.example.svfrms.model.ResourceCost;

/** The acceptable range for one sensor of one crop, plus what a breach costs in water/energy. */
public record CropThreshold(Range range, ResourceCost lowCost, ResourceCost highCost) {

    public CropThreshold {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(lowCost, "lowCost");
        Objects.requireNonNull(highCost, "highCost");
    }

    public ResourceCost costFor(Breach breach) {
        return switch (breach) {
            case LOW -> lowCost;
            case HIGH -> highCost;
            case INVALID -> ResourceCost.NONE;
        };
    }
}
