# Design patterns used

We only used a pattern where it fixes a concrete problem we found in the legacy code. Each entry says
what that problem was, where the pattern lives, and what we gain.

## 1. Strategy: alert rules
* **Problem:** `processSensor()` (L141-205) was one 65-line if/else chain mixing every crop and every sensor. Adding
  an alert type meant editing it.
* **Solution:** `AlertRule` interface with `CropThresholdRule`, `FacilityThresholdRule`, `SensorFailureRule`.
  `ChamberMonitor` just loops over a list of rules.
* **Gain:** new alert types are new classes (OCP). Each rule is tested on its own. `SensorFailureRule` was added
  this way without touching anything else.

## 2. Observer: readings, alerts and device events
* **Problem:** `handleEvent()` (L62-88) was a string switch where one call printed, counted, logged and tracked
  summaries all at once. Everything was coupled to everything.
* **Solution:** three small subjects with listeners:
  * `SensorHub` → `ReadingListener` (`SensorLog`, `ChamberMonitor`)
  * `AlertService` → `AlertListener` (`ChamberSummaryTracker`, `ConsoleEventPrinter`, `ResourceAccountant`, `DeviceResponder`)
  * `DeviceController` → `DeviceListener` (`ChamberSummaryTracker`, `ConsoleEventPrinter`, `DeviceActivationCounter`)
* **Gain:** each reaction is its own class with one job (SRP). Adding a dashboard or an SMS notifier means
  writing one listener and registering it in `FacilityAssembler`.

## 3. Command: device requests
* **Problem:** requests crossed threads as strings like `"CH1|Cooling Fan"` that were split again at L269. A typo
  compiled fine and silently did nothing.
* **Solution:** `ControlCommand` with `ActivateDeviceCommand` (the receiver is `DeviceController`). Commands are queued
  by `QueuedControlDispatcher` and run on the control thread, or run immediately by `ImmediateControlDispatcher`.
* **Gain:** type-safe requests, the queue holds behaviour instead of text, and "when/where it runs" is separate
  from "what it does". That separation is also what makes the deterministic golden tests possible.

## 4. Template Method: reports
* **Problem:** the daily report header and totals were written twice, once for the console (L210-214) and once for
  the file (L314-319). Changing the format meant two edits that could drift apart.
* **Solution:** `TemplateReport.generate()` is `final` and fixes the skeleton (banner + body). Subclasses
  (`DailyReport`, `ArchiveReport`, `WeeklyCropReport`, `WaterUsageReport`, `EnergyConsumptionReport`,
  `NutrientConsumptionReport`) only provide `title()` and `addBody()`. The shared totals are written once in `addTotals()`.
* **Gain:** all reports look consistent, and the four new reports from the brief took about 15 lines each.

## 5. Registry (plus data-driven configuration): crops and devices
* **Problem:** crops existed only as `if(crop.equals("Tomato"))` blocks, so adding Kale meant editing core logic (OCP violation, brief 4.2.ii and 4.5).
* **Solution:** `CropProfile` record + `CropRegistry` interface (`InMemoryCropRegistry`), filled by `CropProfileLoader`
  from `crops.properties`. The same idea applies to `DeviceRegistry` and to `ResponsePlan` (which device answers which alert).
* **Gain:** adding or removing a crop is a data edit, or a `--crops` file at runtime. We added Kale, Bell Pepper,
  Bok Choy and Eggplant with zero Java changes.

## 6. Dependency Injection with a single Composition Root
* **Problem:** everything was `public static`, so nothing could be swapped or tested in isolation (DIP violation).
* **Solution:** every class receives its collaborators through its constructor. `FacilityAssembler` is the only
  place that calls `new` on concrete classes and wires them together.
* **Gain:** tests build the same object graph with a fixed seed, an immediate dispatcher and a temp folder. Changing
  an implementation is a one-line change in one file.

## 7. Producer-Consumer (concurrency pattern)
* **Problem:** a hand-written `wait()/notifyAll()` queue that swallowed interrupts (C8).
* **Solution:** `ArrayBlockingQueue` inside `QueuedControlDispatcher`, with a poison pill for shutdown.

## Patterns we considered but did not use
* **Builder** for readings or reports: records with 4–6 fields are already clear, so a builder would only add code.
* **Factory Method / Abstract Factory** for devices: the registry plus one `new` per device in the composition
  root is simpler, and there is no family of related products to justify an abstract factory.
* **Singleton** for the registries or services: this was effectively the legacy design (global statics), and
  it is exactly what made the code untestable. One instance is created in the composition root instead.
* **State** for chambers: chambers have no modes or transitions, only values.
