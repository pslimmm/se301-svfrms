# Study guide: knowing our code well enough to defend it

The brief warns that "failure to demonstrate understanding will impact your grade". Each of us should be able to
explain any class below and answer the 15 questions at the end without notes. A good way to prepare is to take turns
quizzing each other.

## The big picture in 30 seconds
Chamber tasks generate readings and publish them to the `SensorHub`. The `ChamberMonitor` updates the chamber state and runs
every `AlertRule`. When a rule fires, `AlertService` stores the alert and tells its listeners: the console printer prints it,
the summary tracker counts it, the resource accountant adds water/energy, and the device responder asks the dispatcher for a
device. The device command runs on the control thread and changes the chamber state. At the end, `EndOfDayService` archives
the alerts, prints the daily report and writes the archive file. `FacilityAssembler` builds and wires all of this.

## Class by class

### `app`: starting and wiring
| Class | What it does / why it exists | Principle / pattern |
|---|---|---|
| `Main` | Entry point. Parses options, builds the facility, runs it. Nothing else | SRP |
| `CommandLineOptions` | Turns `--seed 42 --sequential ...` into an `AppConfig`. No arguments gives exactly the legacy run | SRP |
| `AppConfig` | A record holding every setting for one run (chambers, seed, archive folder, ...) | immutability |
| `FacilityAssembler` | **Composition root.** The only class that calls `new` on concrete classes and connects them. Listener order is set here | DIP, Dependency Injection |
| `Facility` | A record returned by the assembler so `Main` and tests can reach the parts they need | – |

### `model`: plain data
| Class | What / why | Principle / pattern |
|---|---|---|
| `SensorType` | Enum of the 5 sensors. Replaces raw strings like `"Soil"` that could be mistyped | removes primitive obsession |
| `Breach` | HIGH / LOW / INVALID: which way a reading left its range | – |
| `AlertSeverity` | INFO / WARNING / CRITICAL from the brief. Kept internal so output is unchanged | – |
| `Range` | min/max with strict `<`/`>` checks like the legacy code. A missing bound is infinity | – |
| `ResourceCost` | Water litres + energy kWh caused by an alert | – |
| `SensorReading` | Immutable record of one measurement, so it is safe to pass between threads | thread safety by immutability |
| `Alert` | Immutable alert. `message` keeps the exact legacy text | immutability |

### `config`
| Class | What / why | Principle / pattern |
|---|---|---|
| `ConfigFile` | Reads `key=value` files and keeps line order (chambers run in the listed order) | SRP |

### `crop`: crops are data
| Class | What / why | Principle / pattern |
|---|---|---|
| `CropProfile` | Record: crop name plus a threshold per sensor. Missing sensor means no rule, e.g. Lettuce has no humidity rule, like in the legacy code | OCP (data, not code) |
| `CropThreshold` | Range plus the cost of a low or high breach | – |
| `CropRegistry` | Interface: register / remove / find crops | Registry, DIP |
| `InMemoryCropRegistry` | `ConcurrentHashMap` implementation (read by many chamber threads) | thread safety |
| `CropProfileLoader` | Builds profiles from `crops.properties`. A crop is found by its `.name` key | OCP |

### `chamber`: per-chamber state
| Class | What / why | Principle / pattern |
|---|---|---|
| `ChamberState` | Current temperature/humidity/... of one chamber. `synchronized` methods, and `adjust()` is an atomic read-modify-write (fixes C5) | thread safety |
| `ChamberStateStore` | `computeIfAbsent` lookup, so exactly one state per chamber (fixes C1, the NPE we saw) | thread safety |
| `ChamberSummary` | Alert/action counts for the report. Synchronized (fixes C4) | thread safety |
| `ChamberSummarySnapshot` | Immutable copy for reports | immutability |
| `ChamberSummaryTracker` | Listens to alerts and device events and updates the summaries | Observer |

### `sensor`
| Class | What / why | Principle / pattern |
|---|---|---|
| `SensorSource` | Interface "give me one cycle of readings". Real hardware could implement it later | DIP |
| `RandomSensorSimulator` | Legacy random ranges with an injected `Random`, so a seed gives a repeatable run | DIP, testability |
| `SensorHub` | Publishes readings to listeners. Unknown sensor names are only logged, like in the legacy code | Observer (subject) |
| `ReadingListener` | Observer interface for readings | ISP |
| `SensorLog` | Timestamped "Processing ..." lines. `ConcurrentLinkedQueue` (fixes C3) | Observer, thread safety |

### `alert`
| Class | What / why | Principle / pattern |
|---|---|---|
| `AlertRule` | Interface: reading in, optional alert out | **Strategy** |
| `CropThresholdRule` | One rule for every crop using its profile: "CH1 Tomato High Temp" | Strategy, OCP |
| `FacilityThresholdRule` | Crop-independent limits from `facility.properties`: light < 500, nutrient < 40 | Strategy |
| `SensorFailureRule` | NaN/infinite reading gives a CRITICAL "Sensor Failure" alert (from the brief) | Strategy, OCP |
| `AlertService` | Stores active/archived alerts and counters under one lock, and notifies listeners **after** releasing the lock | Observer (subject), thread safety |
| `AlertSnapshot` | Consistent copy of counts and lists | immutability |
| `AlertListener` | Observer interface for alerts | ISP |
| `AlertArchiver` | The legacy `archiveAlerts()`: archive, note it in the report log, print the message. Never holds two locks (fixes C7) | SRP |

### `device` and `device.devices`
| Class | What / why | Principle / pattern |
|---|---|---|
| `Device` | Tiny interface: `name()` + `activate(state)` | ISP, LSP |
| `CoolingFan`, `WaterPump`, `NutrientInjector`, `Humidifier`, `Dehumidifier`, `GrowLight` | Legacy devices with the exact same effects and limits | OCP |
| `HeatingUnit`, `VentilationSystem`, `ShadeController` | Devices from the brief, available but not triggered by default | OCP |
| `DeviceRegistry` | Finds a device by name | Registry |
| `DeviceController` | Runs a device on a chamber and notifies listeners. The *receiver* of commands | Observer (subject), Command receiver |
| `DeviceActivation` / `DeviceListener` | Event + observer interface for "a device ran" | Observer |
| `DeviceActivationCounter` | Counts runs per device for the water/energy/nutrient reports | Observer |
| `ResponsePlan` | Which device answers which alert, read from `facility.properties` | data-driven, OCP |
| `DeviceResponder` | Alert listener that asks the dispatcher for the matching device | Observer |

### `control`: device commands and threading
| Class | What / why | Principle / pattern |
|---|---|---|
| `ControlCommand` | `execute()`, replacing the `"CH1|Cooling Fan"` strings | **Command** |
| `ActivateDeviceCommand` | "Switch on device X in chamber Y" | Command |
| `ControlDispatcher` | Interface: request a device. Decides when and where it runs | DIP |
| `QueuedControlDispatcher` | `ArrayBlockingQueue(5)` + worker thread. Restores interrupts, survives failing commands, drains with a poison pill (fixes C8) | Producer-Consumer |
| `ImmediateControlDispatcher` | Runs the command straight away. Used for `--sequential` and golden tests | – |

### `metrics`
| Class | What / why | Principle / pattern |
|---|---|---|
| `ResourceUsageTracker` | Water/energy totals as `AtomicLong` (fixes C2). Keeps legacy `irrigationCycle()` / `feedingCycle()` | thread safety |
| `ResourceAccountant` | Alert listener that adds the crop's cost for that alert | Observer |

### `report`
| Class | What / why | Principle / pattern |
|---|---|---|
| `ReportGenerator` | Interface: snapshot in, lines out | ISP |
| `TemplateReport` | `final generate()` = banner + `addBody()`. The shared totals are written once | **Template Method** |
| `DailyReport`, `ArchiveReport` | The two legacy reports, same text | Template Method |
| `WeeklyCropReport`, `WaterUsageReport`, `EnergyConsumptionReport`, `NutrientConsumptionReport` | The brief's reports, printed with `--all-reports` | Template Method, OCP |
| `FacilitySnapshot` / `SnapshotCollector` | One immutable picture of everything, so reports need no locks (fixes C6) | immutability |
| `ReportLog` | Synchronized list of "Archived N alerts" / "Daily Report" entries | thread safety |
| `ReportWriter`, `ConsoleReportWriter`, `FileReportWriter` | *Where* lines go, kept separate from *what* the lines are | SRP, DIP |
| `ReportSet`, `ReportService` | Bundle of reports + the service that snapshots, generates and writes | SRP |

### `console`
| Class | What / why | Principle / pattern |
|---|---|---|
| `ConsoleEventPrinter` | Prints `[CH1][Tomato][ALERT] ...` and the green `[CONTROL]` lines exactly as in the legacy code | Observer, SRP |
| `ConsoleViews` | Legacy `showAlerts()` / `showLogs()` | SRP |
| `CropCatalogPrinter` | `--list-crops` | SRP |

### `service` and `concurrency`
| Class | What / why | Principle / pattern |
|---|---|---|
| `ChamberMonitor` | The new `processSensor()`: update state, run rules. That's all | SRP, Strategy client |
| `ChamberTask` | `Runnable` doing 20 cycles for one chamber. Stops on interrupt. Replaced `extends Thread` | LSP |
| `EndOfDayService` | Archive, then daily report, then file, in a fixed order (fixes C9) | – |
| `FacilitySimulation` | Executors, timeouts, error reporting, orderly shutdown (fixes C10) | – |
| `NamedThreadFactory` | Readable thread names: chamber-1, control-1 | – |

## Testing, in one paragraph
`LegacyHarness` runs the **untouched** legacy code (copied to `src/test/java/org/example/legacy`) with fixed inputs and
records golden files. `GoldenRegressionTest` runs our code with the same inputs and demands identical output. The stress tests
use 16 threads to prove the fixes, and `LegacyConcurrencyDemoTest` shows the same bugs happening in the old code.

---

## 15 questions an examiner is likely to ask

**1. How do you know you didn't change the behaviour?**
Before refactoring, we wrote a harness that drives the original code with a fixed seed (all six legacy crops, 20 cycles) and a
set of boundary readings (exactly 28, 28.5, 22, 21.9, ...). We saved its console output, sensor log and report file as
golden files. `GoldenRegressionTest` runs the new code on the same inputs and they match byte for byte. To make the legacy
output repeatable we ran it single-threaded and drained the control queue after each reading, which is exactly what the control thread does.

**2. Walk me through adding a new crop, e.g. Kale.**
Add a block to `crops.properties` (`kale.name=Kale`, `kale.temperature.min=15`, ...). `CropProfileLoader` finds it by the
`.name` key, it goes into the `CropRegistry`, and the single `CropThresholdRule` checks every crop the same way. Add `CH5=Kale` to
a chambers file to grow it. No Java file changes. At runtime you can also pass `--crops myfile.properties`. Removing a crop means deleting the block.

**3. Why Strategy for the alert rules?**
The legacy `processSensor()` was one long if/else covering every crop × sensor. Each kind of check (crop range, facility
limit, sensor failure) varies independently, so each became a class behind `AlertRule`. The monitor just loops over a list.
Adding the Sensor Failure alert from the brief was one new class and one line in the assembler.

**4. Why Observer, and why does listener order matter?**
`handleEvent()` mixed printing, counting and summaries in one string switch. With Observer, each reaction is its own class and
new ones (a dashboard, say) can be added without touching existing code. Order matters only for console output: the printer is
registered before the device responder, so in sequential mode the alert line prints before the device lines, as in the legacy code.

**5. What does the Command pattern buy you here?**
Requests used to cross threads as `"CH1|Cooling Fan"` strings that were split again on the other side. Now they're
typed `ActivateDeviceCommand` objects. Because the command is separate from when it runs, we have two dispatchers: a queue
on a worker thread (production) and an immediate one (tests and `--sequential`). The callers don't know which one they're using.

**6. Where's Template Method and why not just a helper method?**
`TemplateReport.generate()` is `final`: it always adds the `===== TITLE =====` banner and then calls `addBody()`. That guarantees
every report has the same shape, and the shared totals live in one place. The legacy code wrote them twice and they could
drift. The four new reports are about 15 lines each.

**7. Explain your fix for the race in `getChamberState`.**
The legacy code checked `containsKey` and then called `put` on a `HashMap`, which is two separate steps. Two threads can both see "missing"
and create two states, or one can read while another resizes the map and get `null`. We actually saw the NPE in our first run.
`ConcurrentHashMap.computeIfAbsent` does the check and the insert as one atomic step.

**8. Why are totals `AtomicLong` and not `synchronized`?**
`total += 2` is read, add, write. Two threads can read the same old value and one update is lost (our demo test lost about 85 %
of updates). `AtomicLong.addAndGet` is atomic without a lock. Both would be correct, but an atomic is simpler for a single number.

**9. Where could the legacy code deadlock, and how did you prevent it?**
(a) `generateDailyReport` and `archiveAlerts` nest `alertLock` then `reportLock`. They happen to use the same order, but any method
taking them in the opposite order would cause a circular wait. (b) A reachable hang: if the control thread dies, chamber threads wait forever on
the full queue and `main` waits forever on `join`, and since interrupts are swallowed, nothing can break it. Our rule is that **no method
holds two locks at once**, and we never call listeners or block on the queue while holding a lock. With no nesting there can be no cycle.
We still documented a global order (ChamberState → ChamberSummary → AlertService → ReportLog) in case someone needs two later.
The queue now restores interrupts and survives failing commands.

**10. Why `synchronized` instead of `ReentrantLock` / `ReadWriteLock`?**
Every critical section is a few lines inside one object guarding its own fields. We never need tryLock, timeouts, fairness or
many readers with rare writers. `synchronized` is enough, and it is harder to get wrong (no forgotten `unlock()`). We would
switch if we needed one of those features.

**11. Why notify listeners outside the lock in `AlertService.raise`?**
Listeners are "foreign" code. One of them puts a command on a blocking queue. If we held the alert lock while blocked on a
full queue, and the worker thread needed the alert lock, we would deadlock. Calling outside the lock rules that out, and it also
keeps the lock held for less time.

**12. How do threads shut down cleanly now?**
Chamber tasks run on a fixed pool and we wait for each `Future` with a timeout, reporting any failure. Then `stopAndDrain()` puts a
poison pill at the back of the queue, so every earlier command still runs before the worker exits. Then we call `shutdown` and
`awaitTermination`, with `shutdownNow()` in `finally` as a safety net. Archive and report run afterwards in a fixed order.

**13. Show me Liskov and Interface Segregation in your code.**
LSP: the legacy `ControlThread extends Thread` broke `interrupt()`, which is part of `Thread`'s contract. We use `Runnable` plus
executors instead, and our tasks honour interrupts. Every `Device`, `AlertRule`, `ReportWriter` and `ControlDispatcher`
implementation can replace another without the caller noticing. ISP: our interfaces have one or two methods each. For example,
`ConsoleEventPrinter` implements both `AlertListener` and `DeviceListener` rather than one fat event interface.

**14. Where is Dependency Inversion visible?**
`ChamberMonitor` knows only `List<AlertRule>`, `AlertService` and `ChamberStateStore`. `DeviceResponder` knows only `ControlDispatcher`.
`Random`, `Clock` and `PrintStream` are injected. `FacilityAssembler` is the only place with concrete `new` calls. That is why
the golden tests can swap in a seeded `Random`, the immediate dispatcher and a temp folder without changing production code.

**15. The brief lists features the legacy code didn't have. What did you do?**
We had to preserve behaviour, so we added them in a way that leaves the default output unchanged (see `ASSUMPTIONS.md`).
Severity is stored on every alert but not printed. The Sensor Failure alert only fires on NaN/infinite readings, which the simulator
never produces. Heating Unit, Ventilation and Shade Controller are registered but only triggered if you add a `response.` line. The weekly crop,
water, energy and nutrient reports print with `--all-reports`. Where the brief's example thresholds differ from the code
(e.g. Strawberry soil), we kept what the code actually did.

### Quick-fire extras
* *Why records?* Immutable, so they are thread-safe to share, and short to write.
* *Why keep `irrigationCycle()` when nobody calls it?* The brief forbids removing features. It now lives in `ResourceUsageTracker`.
* *Why is `FacilityAssembler` the longest class?* Wiring is its only job. It holds no logic, just `new` and `addListener` calls in one place.
* *What would you do next?* Real sensor adapters behind `SensorSource`, a JSON/YAML crop file, and per-chamber executors if the facility grows.
