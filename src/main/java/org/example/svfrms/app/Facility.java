package org.example.svfrms.app;

import org.example.svfrms.alert.AlertService;
import org.example.svfrms.chamber.ChamberSummaryTracker;
import org.example.svfrms.console.ConsoleViews;
import org.example.svfrms.crop.CropRegistry;
import org.example.svfrms.device.DeviceRegistry;
import org.example.svfrms.metrics.ResourceUsageTracker;
import org.example.svfrms.sensor.SensorHub;
import org.example.svfrms.sensor.SensorLog;
import org.example.svfrms.service.EndOfDayService;
import org.example.svfrms.service.FacilitySimulation;

/** The fully wired system returned by {@link FacilityAssembler}. Tests and Main use the parts they need. */
public record Facility(
        SensorHub sensors,
        CropRegistry crops,
        DeviceRegistry devices,
        ResourceUsageTracker usage,
        AlertService alerts,
        ChamberSummaryTracker summaries,
        SensorLog sensorLog,
        ConsoleViews views,
        EndOfDayService endOfDay,
        FacilitySimulation simulation) {
}
