# Legacy System Analysis – `SVFRMS.java`

All line numbers refer to the untouched copy in [`docs/legacy/SVFRMS.java`](legacy/SVFRMS.java) (393 lines, one class).

## 1. What the legacy program does

`main()` (L349-392) starts one `ControlThread` and four `ChamberThread`s (CH1 Tomato, CH2 Strawberry,
CH3 Lettuce, CH4 Chili). Each chamber thread does 20 cycles. In each cycle it generates five random readings
(Temperature, Humidity, Soil, Light, Nutrient) and pushes each one through `processSensor()`. `processSensor()`
checks the reading against hard-coded thresholds for the crop. When a reading is out of range it records an alert,
adds water/energy usage, and puts a request such as `"CH1|Cooling Fan"` on a bounded queue. The control thread takes
requests off that queue and changes the chamber's simulated state. When the chambers finish, two more threads
archive the alerts and print the daily report, and finally a summary file is written to `archive/daily_report.txt`.

## 2. Responsibilities packed into the one class

| # | Responsibility | Where |
|---|---|---|
| R1 | Global application state (alerts, logs, reports, totals, maps) | L10-31 |
| R2 | Chamber state and summary data model | `ChamberState` L33-39, `ChamberSummary` L41-46 |
| R3 | Lazy chamber lookup | `getChamberState` L48, `getChamberSummary` L55 |
| R4 | Event dispatch, console formatting (incl. ANSI colours) and bookkeeping | `handleEvent` L62-88 |
| R5 | Device behaviour (6 devices) | `activateControl` L90-129 |
| R6 | Producer side of a hand-written bounded queue | `addControlRequest` L131-139 |
| R7 | Crop knowledge, alert rules, resource accounting, device decisions | `processSensor` L141-205 |
| R8 | Daily report formatting | `generateDailyReport` L207-218 |
| R9 | Alert archiving | `archiveAlerts` L220-241 |
| R10 | Resource cycles (never called) | `irrigationCycle` L243, `feedingCycle` L247 |
| R11 | Device worker thread (consumer) | `ControlThread` L251-273 |
| R12 | Sensor simulation | `ChamberThread` L275-295 |
| R13 | Console dumps (never called) | `showAlerts` L297, `showLogs` L303 |
| R14 | File I/O for archive report | `saveArchiveReport` L309-347 |
| R15 | Composition/wiring and thread orchestration | `main` L349-392 |

## 3. Code smells

| Smell | Evidence |
|---|---|
| **God class / Large class** | Everything above lives in `SVFRMS`. |
| **Long method** | `processSensor` is 65 lines of nested `if/else` (L141-205). |
| **Duplicated code** | The pattern `handleEvent("TRACK_ALERT"...); handleEvent("CREATE_ALERT"...); addControlRequest(...)` is copy-pasted ~20 times (L148-202). `activateControl` repeats the same 4-line block 6 times (L93-128). |
| **Primitive obsession / stringly-typed** | Sensor types (`"Temperature"`, `"Soil"`), devices (`"Cooling Fan"`), event types (`"CREATE_ALERT"`) and queue messages (`chamber + "\|" + control`, parsed again with `split("\\|")` at L269) are all raw strings. A typo compiles fine and silently does nothing. |
| **Magic numbers** | Thresholds (`28`, `22`, `500`, `40`), effects (`-5`, `+20`, `+15`), costs (`+=10`, `+=50`) are inline literals. |
| **Switch statement smell** | `handleEvent` (L62) and `activateControl` (L90) are if-chains on strings. |
| **Global mutable state** | Every field is `public static` and non-final (L10-31). Any class could modify any of it. |
| **Dead code** | `irrigationCycle`, `feedingCycle`, `showAlerts`, `showLogs`, the `ALERT_LOG` branch (L66), `waterPumpLock`, `nutrientLock` (L28-29) are never used. Commented-out `Thread.sleep` lines (L224, L385). |
| **Inconsistent rules** | Only Tomato/Strawberry have humidity rules, only Tomato has a soil rule, only Tomato/Strawberry use energy. Spinach and Cucumber have rules but are never run. Nobody can tell whether this is intentional. |
| **Swallowed exceptions** | `catch(Exception e){}` at L134 and L258; `saveArchiveReport` turns every failure into a printed message (L344). |
| **Duplicated report format** | The daily report header and counters are written twice: L210-214 (console) and L314-319 (file). |
| **Mutable "lock" fields** | `public static Object reportLock = new Object();` — anyone can reassign the lock object. |

## 4. SOLID violations

| Principle | Violation | Evidence |
|---|---|---|
| **S**RP | `SVFRMS` has 15 unrelated reasons to change (table in §2). `handleEvent` mixes logging, printing, counting and summary tracking. `processSensor` mixes state update, crop rules, accounting and device control. | L62-88, L141-205 |
| **O**CP | Adding a crop (e.g. Kale) means editing `processSensor` and adding another `else if` block per sensor. Adding a device means editing `activateControl`. Adding an event type means editing `handleEvent`. | L147-170, L93-128, L63-87 |
| **L**SP | `ChamberThread extends Thread` and `ControlThread extends Thread` (L251, L275). They inherit the whole `Thread` API (`stop`, `setDaemon`, `interrupt`...) but `ControlThread` breaks the `interrupt()` contract: it swallows `InterruptedException` (L258) and keeps waiting, so a caller who interrupts it gets different behaviour from what `Thread` promises. Inheritance is used to reuse code rather than to model an "is-a" relationship. | L251-273, L275-295 |
| **I**SP | There are no interfaces at all. `handleEvent(type, chamber, crop, message)` is one "fat" entry point every caller must use, even though many arguments are ignored (callers pass `""` for crop, L95). | L62, L95-127 |
| **D**IP | High-level policy (`processSensor`) depends directly on concrete details: `System.out`, `new Date()`, the static `Random`, the file system path `archive/`, and static lists. Nothing can be swapped or tested in isolation. | L7-8, L31, L64, L67, L311 |

## 5. Concurrency problems

Threads involved: 4 × `ChamberThread` (writers), 1 × `ControlThread`, plus the archive and report threads at the end.

| ID | Problem | Kind | Evidence | Consequence |
|---|---|---|---|---|
| C1 | `getChamberState` / `getChamberSummary` check-then-act on a plain `HashMap` | Race condition, unsafe collection | L48-53, L55-60 | Two threads can both see "missing" and both `put`; concurrent `put`s can corrupt the map during resize. **Observed:** our first run crashed CH1 with `NullPointerException: Cannot assign field "temperature" because "state" is null at processSensor(SVFRMS.java:146)`. |
| C2 | `totalWaterUsage += ...`, `totalEnergyUsage += ...`, `totalAlerts++`, `totalAlertsGenerated++` from 4 threads with no lock | Race condition (lost update) | L74-75, L148-186, L291-292 | Totals in the report can be lower than the real values. |
| C3 | `alerts.add`, `sensorLogs.add` on shared `ArrayList` | Unsafe collection | L64, L73 | Lost elements, `ArrayIndexOutOfBoundsException` during internal resize. |
| C4 | `ChamberSummary` counters (`alerts++`, `mitigationActions++`) updated by chamber thread *and* control thread | Race condition | L79-86 | Lost counts; `lastAction`/`lastAlert` may be stale. |
| C5 | `ChamberState` fields: chamber thread writes `state.temperature = value` while the control thread does `state.temperature = Math.max(18, state.temperature - 5)` | Race (read-modify-write) + visibility (non-volatile fields) | L94-124 vs L146-198 | Device effect computed from an outdated value; the printed "Temperature reduced to ..." may not match reality. |
| C6 | `generateDailyReport` reads totals under `alertLock`/`reportLock`, but no writer ever takes those locks | Synchronization issue (false sense of safety) | L208-215 vs L74, L291 | The locks protect nothing; reads can see torn/stale values. |
| C7 | Nested locking `alertLock → reportLock` in two methods | Latent deadlock (lock ordering) | L208-209, L223-230 | Safe *only* because both happen to use the same order. The commented `Thread.sleep(100)` at L224 shows someone was fighting timing problems. Any future method that takes `reportLock` then `alertLock` deadlocks. Two unused locks (L28-29) make this more likely. |
| C8 | `catch(Exception e){}` around `wait()` | Interrupt swallowing / liveness | L134, L258 | Threads cannot be interrupted or cancelled. If the consumer thread dies (e.g. from the C1 NPE, since it also calls `getChamberState`), producers block forever in `addControlRequest` once 5 requests are queued, so the program hangs. |
| C9 | Archive thread and report thread started together with no ordering | Race (non-determinism) | L373-389 | "Active Alerts" in the daily report is sometimes the full count, sometimes 0, depending on scheduling. |
| C10 | Raw `Thread` objects, no executor, no timeout on `join`, no shutdown path | Thread management | L350-371 | No way to bound runtime or recover from a stuck thread. |
| C11 | `reports` list written by archive and report threads | Unsafe collection | L215, L233-236 | Order of "Daily Report" / "Archived N alerts" lines varies run to run. |
| C12 | Console output interleaving: alert lines and control lines from different threads interleave arbitrarily | Non-determinism | L67-76, L70 | Harmless but makes output hard to read and test. |

## 6. Behaviour we must preserve (characterization)

Captured by `LegacyCharacterizationTest` into `src/test/resources/golden/`:

* Alert text formats: `[CH1][Tomato][ALERT] CH1 Tomato High Temp`, facility-wide alerts without crop: `CH1 Low Light Level`.
* Control text format with ANSI green: `\t\u001B[32m[CH1][CONTROL] Cooling Fan Activated\u001B[0m`.
* Thresholds per crop (strict `<` / `>`), and that unknown crops still get Light/Nutrient alerts.
* Water/energy costs per alert, +2 L / +1 kWh per chamber cycle, `irrigationCycle` (+100 L), `feedingCycle` (+20 kWh).
* Device effects and clamps (Cooling Fan −5 °C min 18, Water Pump +20 % max 100, etc.).
* Chamber summaries exist only for chambers that had an alert or action, sorted by chamber id in the file.
* Report file layout, the `reports` list, archived alert list.
* Unknown sensor types are logged but otherwise ignored.
