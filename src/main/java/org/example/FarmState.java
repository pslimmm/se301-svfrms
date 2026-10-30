package org.example;

import java.util.*;

/** Owns farm data. Every mutation and snapshot uses the same private lock. */
public final class FarmState {
    private final RuleEvaluator evaluator;
    private final Object lock = new Object();
    private final List<String> alerts = new ArrayList<>();
    private final List<String> archivedAlerts = new ArrayList<>();
    private final List<String> sensorLogs = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();
    private final Map<String, ChamberState> chambers = new HashMap<>();
    private final Map<String, ChamberSummary> summaries = new HashMap<>();
    private int totalWaterUsage;
    private int totalEnergyUsage;
    private int totalAlertsGenerated;

    public FarmState() {
        this(CropCatalog.legacyDefaults());
    }

    public FarmState(CropCatalog catalog) {
        evaluator = new RuleEvaluator(catalog);
    }

    private static final class ChamberState {
        double temperature = 24, humidity = 70, soilMoisture = 60, lightLevel = 600, nutrientLevel = 70;
        ChamberSnapshot snapshot() {
            return new ChamberSnapshot(temperature, humidity, soilMoisture, lightLevel, nutrientLevel);
        }
    }

    private static final class ChamberSummary {
        int alerts, mitigationActions;
        String lastAction = "No action", lastAlert = "None";
        SummarySnapshot snapshot() {
            return new SummarySnapshot(alerts, mitigationActions, lastAction, lastAlert);
        }
    }

    public record ChamberSnapshot(double temperature, double humidity, double soilMoisture,
                                  double lightLevel, double nutrientLevel) {}
    public record SummarySnapshot(int alerts, int mitigationActions, String lastAction, String lastAlert) {}

    public record Snapshot(List<String> alerts, List<String> archivedAlerts, List<String> sensorLogs,
                           List<String> reports, Map<String, ChamberSnapshot> chamberStates,
                           Map<String, SummarySnapshot> chamberSummaries, int totalWaterUsage,
                           int totalEnergyUsage, int totalAlertsGenerated) {
        public Snapshot {
            alerts = List.copyOf(alerts);
            archivedAlerts = List.copyOf(archivedAlerts);
            sensorLogs = List.copyOf(sensorLogs);
            reports = List.copyOf(reports);
            chamberStates = Map.copyOf(chamberStates);
            chamberSummaries = Map.copyOf(chamberSummaries);
        }

        public int totalAlerts() {
            return alerts.size();
        }
    }

    public record SensorResult(List<String> alerts, List<ControlRequest> controls) {
        public SensorResult {
            alerts = List.copyOf(alerts);
            controls = List.copyOf(controls);
        }
    }

    // These private helpers are called only while holding lock; no mutable value escapes.
    private ChamberState chamberLocked(String chamber) {
        return chambers.computeIfAbsent(Objects.requireNonNull(chamber, "chamber"), key -> new ChamberState());
    }

    private ChamberSummary summaryLocked(String chamber) {
        return summaries.computeIfAbsent(Objects.requireNonNull(chamber, "chamber"), key -> new ChamberSummary());
    }

    private void createAlertLocked(String message) {
        alerts.add(Objects.requireNonNull(message, "message"));
        totalAlertsGenerated++;
    }

    private void trackAlertLocked(String chamber, String message) {
        ChamberSummary summary = summaryLocked(chamber);
        summary.alerts++;
        summary.lastAlert = message;
    }

    private void trackActionLocked(String chamber, String message) {
        ChamberSummary summary = summaryLocked(chamber);
        summary.mitigationActions++;
        summary.lastAction = message;
    }

    public ChamberSnapshot chamber(String chamber) {
        synchronized (lock) {
            return chamberLocked(chamber).snapshot();
        }
    }

    public SummarySnapshot summary(String chamber) {
        synchronized (lock) {
            return summaryLocked(chamber).snapshot();
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    private Snapshot snapshotLocked() {
        Map<String, ChamberSnapshot> chamberCopies = new HashMap<>();
        chambers.forEach((id, state) -> chamberCopies.put(id, state.snapshot()));
        Map<String, SummarySnapshot> summaryCopies = new HashMap<>();
        summaries.forEach((id, summary) -> summaryCopies.put(id, summary.snapshot()));
        return new Snapshot(alerts, archivedAlerts, sensorLogs, reports, chamberCopies, summaryCopies,
            totalWaterUsage, totalEnergyUsage, totalAlertsGenerated);
    }

    public void addUsage(int water, int energy) {
        synchronized (lock) {
            totalWaterUsage += water;
            totalEnergyUsage += energy;
        }
    }

    /** Compatibility bookkeeping: the legacy event helpers remain independent operations. */
    public void handleEvent(String type, String chamber, String message) {
        synchronized (lock) {
            switch (type) {
                case "LOG" -> sensorLogs.add(new Date() + " : " + message);
                case "CREATE_ALERT" -> createAlertLocked(message);
                case "TRACK_ALERT" -> trackAlertLocked(chamber, message);
                case "TRACK_ACTION" -> trackActionLocked(chamber, message);
                default -> { }
            }
        }
    }

    public Snapshot dailyReportSnapshot() {
        synchronized (lock) {
            reports.add("Daily Report");
            return snapshotLocked();
        }
    }

    public int archiveAlerts() {
        synchronized (lock) {
            int count = alerts.size();
            archivedAlerts.addAll(alerts);
            alerts.clear();
            reports.add(count == 0 ? "No alerts to archive" : "Archived " + count + " alerts");
            return count;
        }
    }

    public SensorResult processSensor(String chamber, String crop, String sensor, double value) {
        // Configuration is immutable and evaluation uses only the incoming reading.
        List<RuleEvaluator.Effect> effects = evaluator.evaluate(chamber, crop, sensor, value);
        SensorType type = SensorType.fromLegacyName(sensor).orElse(null);
        synchronized (lock) {
            sensorLogs.add(new Date() + " : Processing " + crop + " " + sensor + " = " + value);
            ChamberState state = chamberLocked(chamber);
            switch (type) {
                case TEMPERATURE -> state.temperature = value;
                case HUMIDITY -> state.humidity = value;
                case SOIL -> state.soilMoisture = value;
                case LIGHT -> state.lightLevel = value;
                case NUTRIENT -> state.nutrientLevel = value;
                case null -> { } // Unknown sensors are still logged without changing readings.
            }
            List<String> messages = new ArrayList<>();
            List<ControlRequest> requests = new ArrayList<>();
            for (RuleEvaluator.Effect effect : effects) {
                trackAlertLocked(chamber, effect.alert());
                createAlertLocked(effect.alert());
                totalWaterUsage += effect.waterUsage();
                totalEnergyUsage += effect.energyUsage();
                messages.add(effect.alert());
                if (effect.control() != null) requests.add(effect.control());
            }
            return new SensorResult(messages, requests);
        }
    }

    public List<String> activateControl(String chamber, String control){
        synchronized (lock) {
            List<String> messages = new ArrayList<>();
            ChamberState state = chamberLocked(chamber);

            if(control.equals("Cooling Fan")){
                state.temperature = Math.max(18, state.temperature - 5);
                trackActionLocked(chamber, "Cooling Fan Activated");
                messages.add("Cooling Fan Activated");
                messages.add("Temperature reduced to " + state.temperature + "C");
            }
            else if(control.equals("Water Pump")){
                state.soilMoisture = Math.min(100, state.soilMoisture + 20);
                trackActionLocked(chamber, "Water Pump Activated");
                messages.add("Water Pump Activated");
                messages.add("Soil moisture increased to " + state.soilMoisture + "%");
            }
            else if(control.equals("Nutrient Injector")){
                state.nutrientLevel = Math.min(100, state.nutrientLevel + 15);
                trackActionLocked(chamber, "Nutrient Injector Activated");
                messages.add("Nutrient Injector Activated");
                messages.add("Nutrient level increased to " + state.nutrientLevel + "%");
            }
            else if(control.equals("Humidifier")){
                state.humidity = Math.min(100, state.humidity + 10);
                trackActionLocked(chamber, "Humidifier Activated");
                messages.add("Humidifier Activated");
                messages.add("Humidity increased to " + state.humidity + "%");
            }
            else if(control.equals("Dehumidifier")){
                state.humidity = Math.max(30, state.humidity - 10);
                trackActionLocked(chamber, "Dehumidifier Activated");
                messages.add("Dehumidifier Activated");
                messages.add("Humidity reduced to " + state.humidity + "%");
            }
            else if(control.equals("Grow Light")){
                state.lightLevel = Math.min(1000, state.lightLevel + 150);
                trackActionLocked(chamber, "Grow Light Activated");
                messages.add("Grow Light Activated");
                messages.add("Light level increased to " + state.lightLevel + " lux");
            }
            return List.copyOf(messages);
        }
    }
}
