package org.example.svfrms.metrics;

import org.example.svfrms.alert.AlertListener;
import org.example.svfrms.crop.CropRegistry;
import org.example.svfrms.model.Alert;

/**
 * Adds the water/energy cost of an alert to the totals, e.g. Tomato low soil moisture uses 50 L.
 * The costs come from the crop profile, so they are data rather than code.
 */
public final class ResourceAccountant implements AlertListener {

    private final CropRegistry crops;
    private final ResourceUsageTracker usage;

    public ResourceAccountant(CropRegistry crops, ResourceUsageTracker usage) {
        this.crops = crops;
        this.usage = usage;
    }

    @Override
    public void onAlert(Alert alert) {
        crops.find(alert.crop())
                .map(profile -> profile.costFor(alert.sensor(), alert.breach()))
                .ifPresent(usage::add);
    }
}
