package org.example;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.example.DeviceType.*;
import static org.example.SensorType.*;
import static org.example.ThresholdRule.Direction.*;

/** Immutable configuration shared by evaluation and the application's chamber setup. */
public final class CropCatalog {
    private final Map<String, CropProfile> profiles;
    private final List<ThresholdRule> globalRules;
    private final List<ChamberAssignment> assignments;

    public record ChamberAssignment(String chamberId, String cropName) {
        public ChamberAssignment {
            Objects.requireNonNull(chamberId, "chamberId");
            Objects.requireNonNull(cropName, "cropName");
            if (chamberId.isBlank() || cropName.isBlank()) {
                throw new IllegalArgumentException("Configured chamber and crop names must not be blank");
            }
        }
    }

    public CropCatalog(List<CropProfile> profiles, List<ThresholdRule> globalRules,
                       List<ChamberAssignment> assignments) {
        Map<String, CropProfile> byName = new HashMap<>();
        for (CropProfile profile : List.copyOf(profiles)) {
            if (byName.putIfAbsent(profile.name(), profile) != null) {
                throw new IllegalArgumentException("Duplicate crop: " + profile.name());
            }
        }
        this.profiles = Map.copyOf(byName);
        this.globalRules = List.copyOf(globalRules);
        ThresholdRule.validateRules(this.globalRules);
        for (CropProfile profile : this.profiles.values()) {
            ThresholdRule.validateRanges(profile.rules(), this.globalRules);
        }
        this.assignments = List.copyOf(assignments);
        Set<String> chambers = new HashSet<>();
        for (ChamberAssignment assignment : this.assignments) {
            if (!this.profiles.containsKey(assignment.cropName())) {
                throw new IllegalArgumentException("Unknown assigned crop: " + assignment.cropName());
            }
            if (!chambers.add(assignment.chamberId())) {
                throw new IllegalArgumentException("Duplicate chamber: " + assignment.chamberId());
            }
        }
    }

    public Optional<CropProfile> profile(String name) {
        return Optional.ofNullable(profiles.get(name));
    }

    public Map<String, CropProfile> profiles() { return profiles; }
    public List<ThresholdRule> globalRules() { return globalRules; }
    public List<ChamberAssignment> assignments() { return assignments; }

    /** Registers a new profile in a new catalog; duplicate names never overwrite profiles. */
    public CropCatalog withProfile(CropProfile profile) {
        Objects.requireNonNull(profile, "profile");
        List<CropProfile> updated = new ArrayList<>(profiles.values());
        updated.add(profile);
        return new CropCatalog(updated, globalRules, assignments);
    }

    /** An assigned profile must first be reassigned or have its chambers unassigned. */
    public CropCatalog withoutProfile(String name) {
        requireProfile(name);
        if (assignments.stream().anyMatch(assignment -> assignment.cropName().equals(name))) {
            throw new IllegalArgumentException("Cannot remove assigned crop: " + name);
        }
        return new CropCatalog(profiles.values().stream().filter(profile -> !profile.name().equals(name)).toList(),
            globalRules, assignments);
    }

    /** Adds a chamber at the end of startup order; an existing chamber requires explicit reassignment. */
    public CropCatalog withAssignment(String chamber, String crop) {
        List<ChamberAssignment> updated = new ArrayList<>(assignments);
        updated.add(new ChamberAssignment(chamber, crop));
        return new CropCatalog(List.copyOf(profiles.values()), globalRules, updated);
    }

    /** Changes an existing chamber's crop without changing its startup position. */
    public CropCatalog reassignChamber(String chamber, String crop) {
        requireProfile(crop);
        requireAssignment(chamber);
        ChamberAssignment replacement = new ChamberAssignment(chamber, crop);
        return new CropCatalog(List.copyOf(profiles.values()), globalRules, assignments.stream()
            .map(assignment -> assignment.chamberId().equals(chamber) ? replacement : assignment).toList());
    }

    /** Removes a chamber from a new catalog; it does not affect any existing application. */
    public CropCatalog withoutAssignment(String chamber) {
        requireAssignment(chamber);
        return new CropCatalog(List.copyOf(profiles.values()), globalRules, assignments.stream()
            .filter(assignment -> !assignment.chamberId().equals(chamber)).toList());
    }

    private void requireProfile(String name) {
        Objects.requireNonNull(name, "name");
        if (!profiles.containsKey(name)) throw new IllegalArgumentException("Unknown crop: " + name);
    }

    private void requireAssignment(String chamber) {
        Objects.requireNonNull(chamber, "chamber");
        if (assignments.stream().noneMatch(assignment -> assignment.chamberId().equals(chamber))) {
            throw new IllegalArgumentException("Unknown chamber: " + chamber);
        }
    }

    /** Exact legacy rule inventory, including omitted rules and asymmetric resource accounting. */
    public static CropCatalog legacyDefaults() {
        return new CropCatalog(List.of(
            new CropProfile("Tomato", List.of(
                new ThresholdRule(TEMPERATURE, ABOVE, 28, "High Temp", COOLING_FAN, 0, 10),
                new ThresholdRule(TEMPERATURE, BELOW, 22, "Low Temp", null, 0, 15),
                new ThresholdRule(HUMIDITY, ABOVE, 80, "High Humidity", DEHUMIDIFIER, 0, 0),
                new ThresholdRule(HUMIDITY, BELOW, 60, "Low Humidity", HUMIDIFIER, 20, 0),
                new ThresholdRule(SOIL, BELOW, 45, "Low Soil Moisture", WATER_PUMP, 50, 0))),
            new CropProfile("Strawberry", List.of(
                new ThresholdRule(TEMPERATURE, ABOVE, 24, "High Temp", COOLING_FAN, 0, 10),
                new ThresholdRule(TEMPERATURE, BELOW, 18, "Low Temp", null, 0, 15),
                new ThresholdRule(HUMIDITY, ABOVE, 85, "High Humidity", DEHUMIDIFIER, 0, 0),
                new ThresholdRule(HUMIDITY, BELOW, 70, "Low Humidity", HUMIDIFIER, 25, 0))),
            new CropProfile("Lettuce", List.of(
                new ThresholdRule(TEMPERATURE, ABOVE, 21, "High Temp", COOLING_FAN, 0, 0),
                new ThresholdRule(TEMPERATURE, BELOW, 15, "Low Temp", null, 0, 0))),
            new CropProfile("Spinach", List.of(
                new ThresholdRule(TEMPERATURE, ABOVE, 20, "High Temp", COOLING_FAN, 0, 0),
                new ThresholdRule(TEMPERATURE, BELOW, 10, "Low Temp", null, 0, 0))),
            new CropProfile("Chili", List.of(
                new ThresholdRule(TEMPERATURE, ABOVE, 32, "High Temp", COOLING_FAN, 0, 0),
                new ThresholdRule(TEMPERATURE, BELOW, 24, "Low Temp", null, 0, 0))),
            new CropProfile("Cucumber", List.of(
                new ThresholdRule(TEMPERATURE, ABOVE, 27, "High Temp", COOLING_FAN, 0, 0),
                new ThresholdRule(TEMPERATURE, BELOW, 18, "Low Temp", null, 0, 0)))
        ), List.of(
            new ThresholdRule(LIGHT, BELOW, 500, "Low Light Level", GROW_LIGHT, 0, 0),
            new ThresholdRule(NUTRIENT, BELOW, 40, "Low Nutrient Level", NUTRIENT_INJECTOR, 0, 0)
        ), List.of(
            new ChamberAssignment("CH1", "Tomato"),
            new ChamberAssignment("CH2", "Strawberry"),
            new ChamberAssignment("CH3", "Lettuce"),
            new ChamberAssignment("CH4", "Chili")
        ));
    }
}
