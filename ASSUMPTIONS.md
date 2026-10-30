# Assumptions

Decisions we made where the brief or the legacy code was unclear. Each one keeps the legacy behaviour unless stated.

1. **Legacy thresholds win over the brief's examples.** The brief lists e.g. Strawberry soil moisture 50-65 %, but the
   legacy code has no Strawberry soil rule. Because the brief says "the goal is not to change system functionality",
   `crops.properties` reproduces exactly the rules the legacy code enforces. Ranges the legacy code ignores are simply left out
   of the data file, and adding them later is a one-line data change.
2. **Archive runs before the daily report.** Legacy starts the archive thread first and the report thread straight after, so the
   result depended on timing (see C9 in `docs/legacy-analysis.md`). We kept the order the intern clearly intended (archive is
   started first) and made it deterministic. "Active Alerts" in the daily report is therefore always 0 after archiving.
3. **Alert severity is internal.** The brief defines INFO/WARNING/CRITICAL, but the legacy output never prints a severity.
   Alerts now carry a severity (High/Low range breaches = WARNING, sensor failure = CRITICAL) that is available to code and
   to the optional extra reports, but the console and file formats are unchanged.
4. **Extra devices are available but not wired to rules.** Heating Unit, Ventilation System and Shade Controller are in the
   brief but not in the legacy code. They are registered devices you can trigger, but no default rule triggers them, because that
   would change the output.
5. **Extra reports are opt-in.** Weekly Crop, Water Usage, Energy Consumption and Nutrient Consumption reports are printed only
   with `--all-reports`. The default run prints exactly what the legacy code printed.
6. **Dead code is kept as features.** `irrigationCycle()`, `feedingCycle()`, `showAlerts()` and `showLogs()` were never called
   but are public features, so they still exist (in `ResourceUsageTracker`, `AlertService` and `SensorLog`).
7. **New crops' thresholds** (Kale, Bell Pepper, Bok Choy, Eggplant) are reasonable values from general horticulture guides, not
   from the brief. They are data and easy to change.
8. **Golden scenario is single-threaded.** True multi-threaded output cannot be compared byte-for-byte, so golden tests drive one
   chamber at a time with a fixed seed. The concurrent run is checked with invariants instead (counts and totals must add up).
9. **Java 25 runtime.** `run.jar` is compiled for Java 25 (`maven.compiler.release=25`) as the brief requires, so it needs a Java 25+ `java`
   on the PATH.
10. **Sensor failure alert** (brief §3.4) is triggered only by invalid readings (NaN/infinite), which the legacy simulator never
    produces, so default output is unchanged.
