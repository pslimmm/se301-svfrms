package org.example.svfrms.app;

import java.io.PrintStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.example.svfrms.alert.AlertArchiver;
import org.example.svfrms.alert.AlertRule;
import org.example.svfrms.alert.AlertService;
import org.example.svfrms.alert.CropThresholdRule;
import org.example.svfrms.alert.FacilityThresholdRule;
import org.example.svfrms.alert.SensorFailureRule;
import org.example.svfrms.chamber.ChamberStateStore;
import org.example.svfrms.chamber.ChamberSummaryTracker;
import org.example.svfrms.config.ConfigFile;
import org.example.svfrms.console.ConsoleEventPrinter;
import org.example.svfrms.console.ConsoleViews;
import org.example.svfrms.control.ControlDispatcher;
import org.example.svfrms.control.ImmediateControlDispatcher;
import org.example.svfrms.control.QueuedControlDispatcher;
import org.example.svfrms.crop.CropProfileLoader;
import org.example.svfrms.crop.CropRegistry;
import org.example.svfrms.crop.InMemoryCropRegistry;
import org.example.svfrms.device.DeviceActivationCounter;
import org.example.svfrms.device.DeviceController;
import org.example.svfrms.device.DeviceRegistry;
import org.example.svfrms.device.DeviceResponder;
import org.example.svfrms.device.ResponsePlan;
import org.example.svfrms.device.devices.CoolingFan;
import org.example.svfrms.device.devices.Dehumidifier;
import org.example.svfrms.device.devices.GrowLight;
import org.example.svfrms.device.devices.HeatingUnit;
import org.example.svfrms.device.devices.Humidifier;
import org.example.svfrms.device.devices.NutrientInjector;
import org.example.svfrms.device.devices.ShadeController;
import org.example.svfrms.device.devices.VentilationSystem;
import org.example.svfrms.device.devices.WaterPump;
import org.example.svfrms.metrics.ResourceAccountant;
import org.example.svfrms.metrics.ResourceUsageTracker;
import org.example.svfrms.report.ArchiveReport;
import org.example.svfrms.report.ConsoleReportWriter;
import org.example.svfrms.report.DailyReport;
import org.example.svfrms.report.EnergyConsumptionReport;
import org.example.svfrms.report.FileReportWriter;
import org.example.svfrms.report.NutrientConsumptionReport;
import org.example.svfrms.report.ReportLog;
import org.example.svfrms.report.ReportService;
import org.example.svfrms.report.ReportSet;
import org.example.svfrms.report.SnapshotCollector;
import org.example.svfrms.report.WaterUsageReport;
import org.example.svfrms.report.WeeklyCropReport;
import org.example.svfrms.sensor.RandomSensorSimulator;
import org.example.svfrms.sensor.SensorHub;
import org.example.svfrms.sensor.SensorLog;
import org.example.svfrms.service.ChamberMonitor;
import org.example.svfrms.service.ChamberTask;
import org.example.svfrms.service.EndOfDayService;
import org.example.svfrms.service.FacilitySimulation;

/**
 * Composition root: the <b>only</b> place that creates concrete classes and connects them. Every other
 * class receives its collaborators through its constructor (Dependency Injection), so to swap an
 * implementation you change one line here and nothing else.
 *
 * <p>Listener registration order matters for console output and mirrors the legacy order: an alert is
 * printed first, and the device it triggers runs after that.
 */
public final class FacilityAssembler {

    private FacilityAssembler() {
    }

    public static Facility assemble(AppConfig config, PrintStream out) {
        Clock clock = Clock.systemDefaultZone();

        CropRegistry crops = loadCrops(config);
        Map<String, String> facilityConfig = ConfigFile.fromClasspath("facility.properties");
        warnAboutUnknownCrops(config, crops);

        // devices
        ChamberStateStore states = new ChamberStateStore();
        DeviceRegistry devices = standardDevices();
        DeviceController controller = new DeviceController(devices, states);
        ControlDispatcher dispatcher = config.sequential()
                ? new ImmediateControlDispatcher(controller)
                : new QueuedControlDispatcher(controller, QueuedControlDispatcher.LEGACY_CAPACITY);

        // observers
        ConsoleEventPrinter printer = new ConsoleEventPrinter(out);
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        DeviceActivationCounter activations = new DeviceActivationCounter();
        ResourceUsageTracker usage = new ResourceUsageTracker();
        controller.addListener(summaries);
        controller.addListener(printer);
        controller.addListener(activations);

        AlertService alerts = new AlertService();
        alerts.addListener(summaries);
        alerts.addListener(printer);
        alerts.addListener(new ResourceAccountant(crops, usage));
        alerts.addListener(new DeviceResponder(ResponsePlan.fromConfig(facilityConfig), dispatcher));

        // alert rules (Strategy): add a new kind of check by adding one line here
        List<AlertRule> rules = new ArrayList<>();
        rules.add(new SensorFailureRule());
        rules.add(new CropThresholdRule(crops));
        rules.addAll(FacilityThresholdRule.fromConfig(facilityConfig));

        // sensors
        SensorLog sensorLog = new SensorLog(clock);
        SensorHub sensors = new SensorHub();
        sensors.subscribe(sensorLog);
        sensors.subscribe(new ChamberMonitor(states, rules, alerts));

        // reports
        ReportLog reportLog = new ReportLog();
        SnapshotCollector collector = new SnapshotCollector(alerts, usage, reportLog, summaries, activations, clock);
        ReportSet reportSet = new ReportSet(new DailyReport(), new ArchiveReport(), List.of(
                new WeeklyCropReport(), new WaterUsageReport(), new EnergyConsumptionReport(), new NutrientConsumptionReport()));
        ReportService reports = new ReportService(collector, reportLog, reportSet,
                new ConsoleReportWriter(out), new FileReportWriter(config.archiveFile()));
        EndOfDayService endOfDay = new EndOfDayService(
                new AlertArchiver(alerts, reportLog, out), reports, out, config.allReports());

        // simulation
        Random random = config.seed().map(Random::new).orElseGet(Random::new);
        RandomSensorSimulator simulator = new RandomSensorSimulator(random);
        List<ChamberTask> tasks = new ArrayList<>();
        config.chambers().forEach((chamber, crop) ->
                tasks.add(new ChamberTask(chamber, crop, simulator, sensors, usage, config.cycles())));
        FacilitySimulation simulation = new FacilitySimulation(tasks, dispatcher, endOfDay, config.sequential());

        return new Facility(sensors, crops, devices, usage, alerts, summaries, sensorLog,
                new ConsoleViews(out, alerts, sensorLog), endOfDay, simulation);
    }

    private static CropRegistry loadCrops(AppConfig config) {
        CropRegistry crops = new InMemoryCropRegistry();
        CropProfileLoader.load(ConfigFile.fromClasspath("crops.properties"), "crops.properties")
                .forEach(crops::register);
        config.extraCrops().ifPresent(file ->
                CropProfileLoader.load(ConfigFile.fromFile(file), file.toString()).forEach(crops::register));
        return crops;
    }

    private static DeviceRegistry standardDevices() {
        DeviceRegistry devices = new DeviceRegistry();
        devices.register(new CoolingFan());
        devices.register(new WaterPump());
        devices.register(new NutrientInjector());
        devices.register(new Humidifier());
        devices.register(new Dehumidifier());
        devices.register(new GrowLight());
        devices.register(new HeatingUnit());
        devices.register(new VentilationSystem());
        devices.register(new ShadeController());
        return devices;
    }

    private static void warnAboutUnknownCrops(AppConfig config, CropRegistry crops) {
        config.chambers().forEach((chamber, crop) -> {
            if (crops.find(crop).isEmpty()) {
                System.err.println("Warning: " + chamber + " grows '" + crop
                        + "' which has no crop profile; only facility-wide rules apply.");
            }
        });
    }
}
