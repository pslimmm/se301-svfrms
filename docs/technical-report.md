# SVFRMS technical report

## 1. Legacy system and preservation contract

The Singapore Vertical Farming Research Management System (SVFRMS) is a finite simulation of sensor monitoring, threshold alerts, queued device control, and a daily archive report. The original implementation placed these responsibilities in a single 393-line class with public static state. Four chamber threads generated readings while one consumer executed controls; reporting and alert archiving followed producer completion. The refactoring preserves this workflow instead of replacing the application. [1, 2]

### Behavior retained

The default assignments remain CH1/Tomato, CH2/Strawberry, CH3/Lettuce, and CH4/Chili. Each produces 20 iterations of Temperature, Humidity, Soil, Light, and Nutrient readings, in that order: 400 readings overall. The integer ranges remain 10-39, 40-99, 20-79, 300-1199, and 20-99. Base usage remains 160 L water and 80 kWh energy, with rule-specific charges added when an alert is detected.

All six original crops remain supported, including Spinach and Cucumber. Comparisons are strict; boundary equality does not trigger that rule. Every violating reading creates another alert. Only Tomato/Strawberry have humidity rules; only Tomato has a soil rule. Global low-light and low-nutrient rules also apply to unknown crop strings. Missing rules were not silently enabled from descriptive specification ranges.

Six device formulas and their clamps are preserved, as are ANSI control messages, sorted chamber summaries, UTF-8 output, report replacement, and the irrigation/feeding helper methods. A queued control transforms the state present when it executes, which can include a newer sensor reading. Low temperature still charges energy for Tomato/Strawberry without a heating action; changing that inconsistency would be a separate feature decision.

### Defects identified in the original source

| Evidence in legacy SVFRMS | Consequence and correction |
| --- | --- |
| Public static maps, lists, counters, and locks | Concurrent writes lost updates and exposed mutable internals. FarmState now owns private data and immutable snapshots. |
| Crop-name branches inside processSensor | Adding a crop required editing core logic. Immutable profiles now supply data to one evaluator. |
| Requests encoded as chamber + pipe + device | A delimiter in a chamber identifier broke routing. ControlRequest carries typed fields. |
| Queue waits swallowed interruption; main joined producers first | Consumer failure could strand blocked producers. Supervision now observes every task and cancels peers. |
| Direct clock, random, output, and file operations | Tests depended on runtime infrastructure. Small injected boundaries enable deterministic and failure tests. |

No circular monitor deadlock was demonstrated in the original source: report/archive paths acquired their two locks in the same order. The consumer-failure hang was a liveness defect, not evidence of a reversed lock order. Default joins prevented reports from overlapping live producers, but could not recover updates already lost during production. [2, 3]

<!-- page -->

## 2. Architecture and SOLID decisions

The implementation uses plain Java 25 with constructor wiring and no production framework. The stable manifest entry point, SVFRMS.main, constructs FarmApplication. The application owns one finite run, its workers, and failure cleanup. Domain data, rule evaluation, simulation, presentation, and persistence have distinct responsibilities.

[component-diagram]

### Responsibilities and transaction boundary

FarmState is the only mutable state owner. It commits a reading, its log, matching alerts, summary changes, and usage under one private lock. Device transformations and action counts are also atomic. RuleEvaluator reads immutable CropCatalog data and returns effects; it has no clock, output, queue, or mutable state. ChamberSimulation obtains SensorReading values through SensorSource. ReportService takes coherent snapshots, renders them, and passes immutable lines to ArchiveWriter.

FarmApplication retains short sensor/control coordination methods instead of introducing forwarding-only service classes. After a state operation returns, it prints messages and submits commands outside the state lock. ConsoleOutput owns compatibility formatting, RandomSensorSource owns the legacy bounds, FileArchiveWriter owns filesystem operations, and LegacyTimestamp formats an injected Clock.

### SOLID applied where it helps

**Single responsibility:** sampling, evaluation, state, output, persistence, and supervision can change independently. **Open/closed:** new crop profiles and assignments extend behavior as data; the Kale demonstration changes no evaluator or device code. **Dependency inversion:** sample generation and persistence are small interfaces, and time/output are supplied through constructors. Deterministic tests replace infrastructure without changing business rules.

There was no domain inheritance hierarchy to repair, so no unsupported claim of an LSP violation is made. The small SensorSource and ArchiveWriter contracts avoid a broad interface that would force unrelated methods on implementations. No Singleton, Observer bus, DI container, class-per-crop hierarchy, or general rule engine was added merely to increase the pattern count. [3, 4]

<!-- page -->

## 3. Concurrency fixes and lifecycle guarantees

### Safety: one state owner and coherent snapshots

All farm data uses one private lock: chamber initialization, readings, device transformations, alerts, counters, histories, and snapshots. This fixes unsafe HashMap publication, concurrent ArrayList mutation, lost counter increments, and partially visible summary/accounting updates. Active-alert count is derived from the active list. Snapshots defensively copy nested values and collections; callers cannot mutate live state.

The normal sensor path maintains generated = active + archived and generated = sum of chamber alerts. Archiving moves the active list and appends its history marker in one transaction; daily reporting captures totals and appends its marker in another. Either legacy report/archive order remains valid. Independent compatibility event helpers retain their original separate semantics and are not a promise of the full sensor-path invariant.

Rule evaluation and clock calls occur before locking. Console callbacks, queue waits, file I/O, and worker joins occur after unlocking. A blocked callback therefore cannot retain the farm-state lock. Queue coordination is separate; there is no nested farm/queue lock acquisition. Immutable per-run catalogs avoid live configuration races.

### Liveness: bounded admission and supervised workers

ControlQueue wraps a capacity-five ArrayBlockingQueue. Submission is interruptible. A sensor operation retains admission through its control submissions, so closing admission drains already-admitted sensor operations as well as queued commands. The consumer checks close state with a bounded poll; no extra queue slot is required for a stop marker. Commands execute in enqueue order, and each accepted command executes once on a successful run.

FarmApplication observes completed tasks through an ExecutorCompletionService. After all producers finish, it closes admission and awaits consumer drain. It then observes both report/archive tasks, shuts down workers, and saves the archive. The run state is NEW, RUNNING, COMPLETED, FAILED, or CANCELLED; a second run on the same instance is rejected.

On failure, the queue aborts, blocked submissions are interrupted, futures are cancelled, and the executor is stopped. Cleanup waits up to five seconds and preserves interruption. A callback that ignores cancellation is diagnosed; daemon workers prevent it from pinning the CLI JVM. This is cooperative cancellation, not a guarantee that arbitrary user callbacks can be forcibly terminated.

### Evidence rather than timing assumptions

Existing tests coordinate full queues, blocked output/writers/clocks, producer interruption, consumer failure, close/drain races, and concurrent first-use initialization with latches/barriers and deadlines. Stage 8 adds five six-worker contention runs: four producers, an archiver, and a report reader synchronize each batch. Each run retains 800 unique alerts and coherent snapshots. Ten full high-alert runs each process 400 readings and execute/archive 300 controls/alerts, with exact usage and terminated workers. Repetition supports the locking argument; it does not prove every possible schedule. [3, 4]

<!-- page -->

## 4. Patterns and crop extension demonstration

| Pattern or design choice | Specific problem solved |
| --- | --- |
| Command: ControlRequest record + DeviceType | Represents asynchronous work without parsing strings or creating a subclass for each device. |
| Strategy: SensorSource | Substitutes scripted samples for random generation while preserving the simulation loop. |
| Immutable catalog | Centralizes crop/rule lookup and ordered assignments; configuration versions are safe to share across workers. It is not a database repository. |
| Constructor injection | Supplies catalog, source, clock, output, and writer explicitly; no framework manages object lifetimes. |
| ArchiveWriter boundary | Keeps persistence replaceable while ReportService owns snapshot selection and report text. |

### Data-only Kale extension

CropExtensionDemo.catalog() registers one CropProfile and assigns it to CH5. The default CLI remains unchanged. The demo keeps all original profiles and four assignments, then adds one chamber. Its inputs below are synthetic demonstrations, not agronomic recommendations; the specification names potential crops but does not supply their operating values.

| Kale / inherited trigger | Consequence | Additional usage |
| --- | --- | --- |
| Temperature below 16 | Low Temp alert; no device | None |
| Temperature above 24 | High Temp alert; Cooling Fan | 3 kWh |
| Soil below 40 | Low Soil Moisture alert; Water Pump | 10 L |
| Global light below 500 | Low Light Level alert; Grow Light | None |
| Global nutrient below 40 | Low Nutrient Level alert; Nutrient Injector | None |

Boundary equality stays in range. Kale has no humidity rule. The demonstration is launched with `java -cp run.jar org.example.CropExtensionDemo`. Its five chambers generate 500 readings; random totals vary. A scripted test produces exactly 100 archived alerts/actions overall, 80 in CH5, 400 L water, and 160 kWh energy.

### Registration, removal, and future changes

withProfile, withoutProfile, withAssignment, reassignChamber, and withoutAssignment return new catalogs. Duplicate crop/chamber names and unknown references fail explicitly. An assigned profile cannot be removed until every affected chamber is reassigned or unassigned. Reassignment retains startup position. A new configuration never changes an application already constructed with the old one.

Each scope permits one boundary per sensor/direction; opposite bounds must leave a nonzero interval, including across global/crop scopes. Same-direction global and crop rules remain additive. Empty and one-sided profiles remain valid. To add a sensor, extend its typed key, sampling, and rule data; to add a device, extend its typed value and atomic transformation. A new report format consumes snapshots. No evaluation branch on Kale was introduced. [3, 4]

<!-- page -->

## 5. Validation and remaining limits

### Validation summary

The Maven suite includes characterization, concurrency, lifecycle, and extension cases plus Stage 8 repetitions. Packaging requires a nonempty suite with no failures, errors, or skipped tests. Deterministic fixtures cover exact thresholds, six device formulas/clamps, helper behavior, log/report text, sampling order, failure paths, and immutability.

| Scenario | Deterministic acceptance values |
| --- | --- |
| Default scripted four-chamber run | 400 logs; 20 alerts/actions; 160 L; 80 kWh; empty queue; no active alerts |
| Five-chamber Kale fixture | 500 logs; 100 alerts/actions; 400 L; 160 kWh; original catalog retained during concurrent replacement |
| High-alert run, repeated 10 times | 400 logs; 300 alerts/actions; 2060 L; 480 kWh; archived report equals state archive |
| State/archive/report contention, repeated 5 times | 800 unique alerts each; generated = active + archived = summed chamber counts; 12000 kWh |

The submission contains Maven source/tests, pom.xml, run.jar, and this report under both README.pdf and report.pdf to satisfy the specification's conflicting filenames. The aliases are byte-identical. There are no production third-party dependencies: JUnit is test-scoped, so the executable needs only Java 25+. Generated ZIP entries are allowlisted and checksummed; packaged CLI smoke checks use fresh working directories. Build/reproduction instructions and the detailed validation record accompany the source. [1, 3]

### Practical limits

COMPLETED means processing and worker cleanup succeeded; the legacy report-write error policy prints a message instead of failing the run. File output truncates the prior report and is not crash-atomic. A synchronous final writer has no timeout. Cancellation is cooperative, and state commit precedes command admission: a failed run can contain alerts whose mitigation did not complete. Exactly-once crash recovery is not provided.

Catalog changes require a new application; there is no UI, file loader, live editing, or persistence. The default clock captures its zone at construction, and zone labels depend on JDK locale data. Histories are unbounded within a finite run. Independent legacy helpers can bypass normal sensor bookkeeping. No stronger validation of arbitrary direct API inputs is claimed.

Heating, ventilation, shade control, severity, sensor-failure handling, and weekly/resource reports are specified but absent from the legacy program. They were not silently added; the backlog defines decisions and acceptance criteria for future work.

### Source and evidence references

[1] docs/se301_project_specification.md, sections 3-6 and 9: functionality, deliverables, preservation constraints, and permitted AI assistance.

[2] Original org.example.SVFRMS at repository commit a2ed2ed; method-level analysis and original line references in docs/refactoring-plan.md.

[3] docs/stage-1-validation.md through docs/stage-8-validation.md; docs/refactoring-plan.md and docs/feature-backlog.md. Earlier reports describe their historical stage.

[4] src/main/java/org/example/ and src/test/java/org/example/: final implementation and automated tests.
