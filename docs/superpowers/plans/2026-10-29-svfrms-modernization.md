# SVFRMS Legacy Modernization & Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the monolithic `SVFRMS.java` into an extensible, thread-safe, multi-component Maven architecture in Java 25 while strictly preserving observable behavior, resolving all concurrency defects, and supporting dynamic crop extensions (Section 4.5).

**Architecture:** Hexagonal / Clean Architecture organized by component (`domain`, `service`, `command`, `concurrency`, `infrastructure`). The system integrates GoF design patterns (Observer for telemetry, Strategy for crop thresholds, Command for actuator dispatch, Builder/Factory for crop profiles, and Template Method for reporting) and modern `java.util.concurrent` primitives (`ArrayBlockingQueue`, `LongAdder`, `ConcurrentHashMap`, Virtual Threads).

**Tech Stack:** Java 25 LTS, Apache Maven 3.9+, JUnit 5, AssertJ, `maven-shade-plugin`.

**Spec:** [`docs/se301_project_specification.md`](file:///c:/Users/peter/Documents/smu/Year%203%20Term%201/apd/proj/docs/se301_project_specification.md) & [`docs/RESEARCH_SYNTHESIS.md`](file:///c:/Users/peter/Documents/smu/Year%203%20Term%201/apd/proj/docs/RESEARCH_SYNTHESIS.md)

---

## Global Constraints

- **Strict Behavior Preservation**: Existing external behavior, simulation output, console logs, and `archive/daily_report.txt` format must be preserved byte-for-byte.
- **No Monolith Disqualification Rule**: Single-file implementation is disqualified; code must be partitioned into logical, focused Maven packages.
- **Java Platform**: Java 25 LTS language level (`<maven.compiler.release>25</maven.compiler.release>`).
- **Dependencies**: Lightweight; zero heavyweight DI or web frameworks (Spring, Guice). Use pure Constructor Injection. Standard test libraries (`junit-jupiter-api`, `junit-jupiter-engine`, `assertj-core`) permitted.
- **Deliverables Required**: Multi-package `src/`, standalone executable `run.jar` (`target/run.jar`), and technical documentation inputs for `README.pdf`.

---

## Review Focus

The five edge cases and failure modes most likely to cause silent regressions or operational failure:
1. **Lost Counter Updates**: Concurrent increments to water/energy counters losing updates under high contention — verified via multi-threaded stress assertions.
2. **Phase Race Condition**: `archiveAlerts()` executing before `generateDailyReport()` clearing active alert counts to 0 — verified via snapshot isolation.
3. **Consumer Queue Death & Hang**: Actuator errors crashing the consumer thread and permanently blocking producers in `wait()` — verified via poisoned pills and bounded timeouts.
4. **Output Drift**: Any deviation in ANSI control logs, alert strings, or daily report syntax — verified via deterministic Golden Master characterization test with fixed random seed.
5. **Crop Dynamic Registration**: Registering a new crop (Kale, Bell Pepper) with boundary limits failing silently or requiring modification to core evaluation logic — verified via OCP unit tests.

---

## File Structure & Package Architecture

```
src/
├── main/
│   └── java/
│       └── org/
│           └── example/
│               └── svfrms/
│                   ├── SvfrmsApplication.java                   # Main Entry Point & Bootstrap
│                   ├── domain/
│                   │   ├── chamber/
│                   │   │   ├── ChamberId.java                   # Value object (record)
│                   │   │   ├── ChamberSnapshot.java             # Immutable state carrier (record)
│                   │   │   ├── ChamberSummarySnapshot.java      # Immutable summary carrier (record)
│                   │   │   ├── ChamberStateTracker.java         # Atomic CAS state manager
│                   │   │   └── ChamberSummaryTracker.java       # Atomic summary accumulator
│                   │   ├── crop/
│                   │   │   ├── CropName.java                    # Strongly-typed identifier (record)
│                   │   │   ├── ThresholdRange.java              # Invariant range validator (record)
│                   │   │   ├── CropProfile.java                 # Immutable multi-range profile (record)
│                   │   │   └── CropRegistry.java                # Thread-safe profile catalog
│                   │   ├── sensor/
│                   │   │   ├── SensorType.java                  # Enum (TEMPERATURE, HUMIDITY, etc.)
│                   │   │   └── SensorReading.java               # Immutable telemetry record
│                   │   ├── actuator/
│                   │   │   └── ActuatorType.java                # Enum (COOLING_FAN, WATER_PUMP, etc.)
│                   │   └── alert/
│                   │       ├── AlertSeverity.java               # Enum (INFO, WARNING, CRITICAL)
│                   │       └── AlertEvent.java                  # Immutable alert carrier (record)
│                   ├── command/
│                   │   ├── DeviceCommand.java                   # GoF Command Interface
│                   │   ├── AbstractDeviceCommand.java           # Shared metrics/metadata
│                   │   ├── CoolingFanCommand.java               # Temperature mitigation
│                   │   ├── WaterPumpCommand.java                # Soil moisture mitigation
│                   │   ├── NutrientInjectorCommand.java         # Nutrient mitigation
│                   │   ├── HumidifierCommand.java               # Humidity elevation
│                   │   ├── DehumidifierCommand.java             # Humidity reduction
│                   │   └── GrowLightCommand.java                # Light illumination
│                   ├── service/
│                   │   ├── telemetry/
│                   │   │   ├── TelemetryObserver.java           # GoF Observer interface
│                   │   │   ├── TelemetryPublisher.java          # Event bus dispatcher
│                   │   │   ├── ThresholdEvaluationObserver.java # GoF Strategy consumer
│                   │   │   └── ConsoleLoggingObserver.java      # Formatting subscriber
│                   │   ├── crop/
│                   │   │   ├── CropThresholdStrategy.java       # Strategy interface
│                   │   │   └── DefaultCropThresholdStrategy.java# Standard 5-metric evaluation
│                   │   ├── actuator/
│                   │   │   └── ActuatorExecutionService.java    # Bounded consumer worker
│                   │   ├── alert/
│                   │   │   └── AlertManagementService.java      # Lock-free queue & snapshotter
│                   │   ├── resource/
│                   │   │   └── ResourceAccountingService.java   # LongAdder meters
│                   │   └── report/
│                   │       ├── AbstractReportGenerator.java     # GoF Template Method
│                   │       ├── ReportFormatterStrategy.java     # PlainText & Markdown strategies
│                   │       └── DailyReportService.java          # Operational report coordinator
│                   ├── infrastructure/
│                   │   ├── storage/
│                   │   │   ├── ReportStoragePort.java           # Output port interface
│                   │   │   └── FileReportStorageAdapter.java    # NIO file writer
│                   │   └── context/
│                   │       └── SvfrmsApplicationContext.java    # Composition Root (Pure DI)
│                   └── concurrency/
│                       ├── BoundedControlQueue.java             # ArrayBlockingQueue wrapper
│                       └── ChamberSimulationTask.java           # Virtual thread runner
└── test/
    └── java/
        └── org/
            └── example/
                └── svfrms/
                    ├── GoldenMasterLegacyTest.java              # Baseline regression net
                    ├── domain/                                  # Domain unit tests
                    ├── service/                                 # Service unit tests
                    └── concurrency/                             # Stress & thread-safety tests
```

---

## Tasks

### Task 1: Baseline Characterization & Golden Master Test Harness

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/org/example/svfrms/GoldenMasterLegacyTest.java`

**Interfaces:**
- Consumes: `org.example.SVFRMS` (legacy class)
- Produces: Deterministic output baseline test verifying legacy stdout and `archive/daily_report.txt`

- [ ] **Step 1: Update `pom.xml` to include JUnit 5 and AssertJ dependencies**

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.4</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.27.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Run `mvn test` to verify build succeeds with test dependencies**

Run: `mvn test`  
Expected: BUILD SUCCESS (0 tests run)

- [ ] **Step 3: Write `GoldenMasterLegacyTest.java` capturing deterministic execution**

Pin random behavior using a fixed seed (`new Random(42L)`) or structured mock sensor inputs to verify identical lines of stdout and `archive/daily_report.txt`.

- [ ] **Step 4: Run `mvn test -Dtest=GoldenMasterLegacyTest`**

Run: `mvn test -Dtest=GoldenMasterLegacyTest`  
Expected: PASS — Baseline output captured and validated.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/test/java/org/example/svfrms/GoldenMasterLegacyTest.java
git commit -m "test: add golden master characterization test for legacy baseline"
```

---

### Task 2: Core Domain Model Extraction (Java 25 Records & Value Objects)

**Files:**
- Create: `src/main/java/org/example/svfrms/domain/chamber/ChamberId.java`
- Create: `src/main/java/org/example/svfrms/domain/chamber/ChamberSnapshot.java`
- Create: `src/main/java/org/example/svfrms/domain/chamber/ChamberSummarySnapshot.java`
- Create: `src/main/java/org/example/svfrms/domain/sensor/SensorType.java`
- Create: `src/main/java/org/example/svfrms/domain/sensor/SensorReading.java`
- Create: `src/main/java/org/example/svfrms/domain/actuator/ActuatorType.java`
- Create: `src/main/java/org/example/svfrms/domain/alert/AlertSeverity.java`
- Create: `src/main/java/org/example/svfrms/domain/alert/AlertEvent.java`
- Create: `src/test/java/org/example/svfrms/domain/DomainRecordsTest.java`

**Interfaces:**
- Produces: Immutable Java 25 records and enums for all domain telemetry, commands, and states.

- [ ] **Step 1: Write unit tests in `DomainRecordsTest.java`**

Assert immutability, non-null validations, and copy constructors for `ChamberSnapshot`, `SensorReading`, and `AlertEvent`.

- [ ] **Step 2: Run `mvn test -Dtest=DomainRecordsTest` to verify failure**

Expected: Compilation Failure (classes not yet created).

- [ ] **Step 3: Implement domain records and enums**

Implement:
- `ChamberId(String value)` with non-blank validation.
- `ChamberSnapshot(double temperature, double humidity, double soilMoisture, double lightLevel, double nutrientLevel)` (record).
- `SensorType` enum (`TEMPERATURE`, `HUMIDITY`, `SOIL_MOISTURE`, `LIGHT`, `NUTRIENT`).
- `SensorReading(ChamberId chamberId, String crop, SensorType sensorType, double value, Instant timestamp)`.
- `ActuatorType` enum (`COOLING_FAN`, `WATER_PUMP`, `NUTRIENT_INJECTOR`, `HUMIDIFIER`, `DEHUMIDIFIER`, `GROW_LIGHT`).
- `AlertSeverity` enum (`INFO`, `WARNING`, `CRITICAL`).
- `AlertEvent(ChamberId chamberId, String crop, AlertSeverity severity, String message, Instant timestamp)`.

- [ ] **Step 4: Run `mvn test -Dtest=DomainRecordsTest` to verify pass**

Run: `mvn test -Dtest=DomainRecordsTest`  
Expected: PASS (all domain records pass validation and immutability checks).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/domain/ src/test/java/org/example/svfrms/domain/
git commit -m "feat: implement immutable domain records and enums"
```

---

### Task 3: Open-Closed Crop Architecture (Strategy Pattern & Dynamic Registry)

**Files:**
- Create: `src/main/java/org/example/svfrms/domain/crop/ThresholdRange.java`
- Create: `src/main/java/org/example/svfrms/domain/crop/CropProfile.java`
- Create: `src/main/java/org/example/svfrms/domain/crop/CropRegistry.java`
- Create: `src/main/java/org/example/svfrms/service/crop/CropThresholdStrategy.java`
- Create: `src/main/java/org/example/svfrms/service/crop/DefaultCropThresholdStrategy.java`
- Create: `src/test/java/org/example/svfrms/domain/crop/CropRegistryTest.java`

**Interfaces:**
- Consumes: `SensorReading`, `ThresholdRange`
- Produces: `CropRegistry` and `CropThresholdStrategy.evaluate(SensorReading, CropProfile) -> EvaluationResult`

- [ ] **Step 1: Write failing tests in `CropRegistryTest.java`**

Test registering baseline profiles (Tomato, Strawberry, Lettuce, Spinach, Chili, Cucumber), boundary evaluation, and dynamic registration of **Kale** and **Bell Pepper** without modifying strategy logic.

- [ ] **Step 2: Run `mvn test -Dtest=CropRegistryTest` to verify failure**

Expected: FAIL (types missing).

- [ ] **Step 3: Implement `ThresholdRange`, `CropProfile`, `CropRegistry`, and `DefaultCropThresholdStrategy`**

- `ThresholdRange(double min, double max)` with validation `$min \le max$`.
- `CropProfile(String name, ThresholdRange temperature, ThresholdRange humidity, ThresholdRange soilMoisture, ThresholdRange light, ThresholdRange nutrient)` with builder.
- `CropRegistry`: `ConcurrentHashMap<String, CropProfile>` with `register(CropProfile)`, `unregister(String)`, and `get(String)`.
- `DefaultCropThresholdStrategy`: evaluates if a `SensorReading` exceeds or falls below profile thresholds and returns optional alerts and device mitigation types.

- [ ] **Step 4: Run `mvn test -Dtest=CropRegistryTest` to verify pass**

Run: `mvn test -Dtest=CropRegistryTest`  
Expected: PASS (dynamic registration and threshold evaluation succeed).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/domain/crop/ src/main/java/org/example/svfrms/service/crop/ src/test/java/org/example/svfrms/domain/crop/
git commit -m "feat: implement Open-Closed crop profile strategy and dynamic registry"
```

---

### Task 4: Thread-Safe State Trackers & Atomic Resource Meters

**Files:**
- Create: `src/main/java/org/example/svfrms/domain/chamber/ChamberStateTracker.java`
- Create: `src/main/java/org/example/svfrms/domain/chamber/ChamberSummaryTracker.java`
- Create: `src/main/java/org/example/svfrms/service/resource/ResourceAccountingService.java`
- Create: `src/test/java/org/example/svfrms/domain/chamber/ChamberStateTrackerTest.java`
- Create: `src/test/java/org/example/svfrms/service/resource/ResourceAccountingServiceTest.java`

**Interfaces:**
- Produces: `ChamberStateTracker.updateAndGet(UnaryOperator<ChamberSnapshot>)`, `ResourceAccountingService.recordWater(long)`, `recordEnergy(long)`

- [ ] **Step 1: Write concurrency stress test in `ResourceAccountingServiceTest.java`**

Spawn 16 parallel threads each incrementing water usage by 2 for 1,000 iterations. Assert that final total equals exactly 32,000 with zero lost updates.

- [ ] **Step 2: Run `mvn test -Dtest=ResourceAccountingServiceTest` to verify failure**

Expected: FAIL (service not found).

- [ ] **Step 3: Implement `ChamberStateTracker`, `ChamberSummaryTracker`, and `ResourceAccountingService`**

- `ChamberStateTracker`: uses `AtomicReference<ChamberSnapshot>` for lock-free compare-and-swap state updates.
- `ChamberSummaryTracker`: thread-safe alert and mitigation action counters.
- `ResourceAccountingService`: uses `java.util.concurrent.atomic.LongAdder` for `waterUsage` and `energyUsage`, and `AtomicInteger` for alert counters.

- [ ] **Step 4: Run tests to verify pass**

Run: `mvn test -Dtest=*TrackerTest,ResourceAccountingServiceTest`  
Expected: PASS (100% atomic accuracy under concurrent stress).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/domain/chamber/ src/main/java/org/example/svfrms/service/resource/ src/test/
git commit -m "feat: implement lock-free chamber state trackers and LongAdder resource meters"
```

---

### Task 5: Actuator Subsystem (Command Pattern & Bounded BlockingQueue)

**Files:**
- Create: `src/main/java/org/example/svfrms/command/DeviceCommand.java`
- Create: `src/main/java/org/example/svfrms/command/CoolingFanCommand.java`
- Create: `src/main/java/org/example/svfrms/command/WaterPumpCommand.java`
- Create: `src/main/java/org/example/svfrms/command/NutrientInjectorCommand.java`
- Create: `src/main/java/org/example/svfrms/command/HumidifierCommand.java`
- Create: `src/main/java/org/example/svfrms/command/DehumidifierCommand.java`
- Create: `src/main/java/org/example/svfrms/command/GrowLightCommand.java`
- Create: `src/main/java/org/example/svfrms/concurrency/BoundedControlQueue.java`
- Create: `src/main/java/org/example/svfrms/service/actuator/ActuatorExecutionService.java`
- Create: `src/test/java/org/example/svfrms/service/actuator/ActuatorServiceTest.java`

**Interfaces:**
- Consumes: `DeviceCommand.execute(ChamberStateTracker, ResourceAccountingService)`
- Produces: `BoundedControlQueue.offer(DeviceCommand, Duration)`, `take()` with Poison Pill termination.

- [ ] **Step 1: Write test in `ActuatorServiceTest.java`**

Test command execution modifying state, energy/water meter increments, and graceful consumer thread termination via poison pill.

- [ ] **Step 2: Run `mvn test -Dtest=ActuatorServiceTest` to verify failure**

Expected: FAIL.

- [ ] **Step 3: Implement Command hierarchy, `BoundedControlQueue`, and `ActuatorExecutionService`**

- `DeviceCommand`: `void execute(ChamberStateTracker tracker, ResourceAccountingService metrics);`
- Concrete commands apply delta matching legacy math (`temperature - 5`, `soilMoisture + 20`, `nutrientLevel + 15`, `humidity +- 10`, `lightLevel + 150`).
- `BoundedControlQueue`: wraps `ArrayBlockingQueue<DeviceCommand>` (capacity 1024).
- `ActuatorExecutionService`: consumer loop taking commands, executing against target chamber tracker, and breaking gracefully upon receiving `PoisonPillCommand`.

- [ ] **Step 4: Run `mvn test -Dtest=ActuatorServiceTest` to verify pass**

Run: `mvn test -Dtest=ActuatorServiceTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/command/ src/main/java/org/example/svfrms/concurrency/ src/main/java/org/example/svfrms/service/actuator/ src/test/
git commit -m "feat: implement GoF Command pattern and bounded blocking actuator queue"
```

---

### Task 6: Telemetry Event Pipeline (Observer Pattern)

**Files:**
- Create: `src/main/java/org/example/svfrms/service/telemetry/TelemetryObserver.java`
- Create: `src/main/java/org/example/svfrms/service/telemetry/TelemetryPublisher.java`
- Create: `src/main/java/org/example/svfrms/service/telemetry/ThresholdEvaluationObserver.java`
- Create: `src/main/java/org/example/svfrms/service/telemetry/ConsoleLoggingObserver.java`
- Create: `src/test/java/org/example/svfrms/service/telemetry/TelemetryPublisherTest.java`

**Interfaces:**
- Consumes: `SensorReading`
- Produces: Decoupled broadcast of telemetry to alerting, actuation, and logging observers.

- [ ] **Step 1: Write failing test in `TelemetryPublisherTest.java`**

Verify that publishing a `SensorReading` triggers both logging observer and threshold evaluation observer without coupling.

- [ ] **Step 2: Run `mvn test -Dtest=TelemetryPublisherTest` to verify failure**

Expected: FAIL.

- [ ] **Step 3: Implement Observer interfaces and concrete listeners**

- `TelemetryObserver`: `void onTelemetry(SensorReading reading);`
- `TelemetryPublisher`: maintains thread-safe `CopyOnWriteArrayList<TelemetryObserver>`.
- `ThresholdEvaluationObserver`: uses `CropThresholdStrategy` to evaluate reading, enqueue mitigation `DeviceCommand`s to `BoundedControlQueue`, and register alerts with `AlertManagementService`.
- `ConsoleLoggingObserver`: formats telemetry log matching legacy stdout format.

- [ ] **Step 4: Run `mvn test -Dtest=TelemetryPublisherTest` to verify pass**

Run: `mvn test -Dtest=TelemetryPublisherTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/service/telemetry/ src/test/
git commit -m "feat: implement GoF Observer pattern for telemetry dispatching"
```

---

### Task 7: Alert Management & Archival Pipeline

**Files:**
- Create: `src/main/java/org/example/svfrms/service/alert/AlertManagementService.java`
- Create: `src/test/java/org/example/svfrms/service/alert/AlertManagementServiceTest.java`

**Interfaces:**
- Produces: `publishAlert(AlertEvent)`, `getActiveAlertsSnapshot()`, `archiveAlerts() -> List<AlertEvent>`

- [ ] **Step 1: Write test for phase race condition in `AlertManagementServiceTest.java`**

Verify that archiving active alerts does not corrupt a concurrent call to `getActiveAlertsSnapshot()`.

- [ ] **Step 2: Run `mvn test -Dtest=AlertManagementServiceTest` to verify failure**

Expected: FAIL.

- [ ] **Step 3: Implement `AlertManagementService`**

- Uses `ConcurrentLinkedQueue<AlertEvent>` for non-blocking alert ingestion.
- `getActiveAlertsSnapshot()`: returns immutable point-in-time snapshot (`List.copyOf`).
- `archiveAlerts()`: drains active queue into thread-safe archive store via atomic batch transfer, returning archived count without locking.

- [ ] **Step 4: Run `mvn test -Dtest=AlertManagementServiceTest` to verify pass**

Run: `mvn test -Dtest=AlertManagementServiceTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/service/alert/ src/test/
git commit -m "feat: implement thread-safe non-blocking alert management and archival service"
```

---

### Task 8: Report Generation Pipeline (Template Method Pattern)

**Files:**
- Create: `src/main/java/org/example/svfrms/service/report/ReportFormatterStrategy.java`
- Create: `src/main/java/org/example/svfrms/service/report/PlainTextReportFormatter.java`
- Create: `src/main/java/org/example/svfrms/service/report/AbstractReportGenerator.java`
- Create: `src/main/java/org/example/svfrms/infrastructure/storage/ReportStoragePort.java`
- Create: `src/main/java/org/example/svfrms/infrastructure/storage/FileReportStorageAdapter.java`
- Create: `src/main/java/org/example/svfrms/service/report/DailyReportService.java`
- Create: `src/test/java/org/example/svfrms/service/report/ReportServiceTest.java`

**Interfaces:**
- Consumes: Metric snapshots from `ResourceAccountingService`, `AlertManagementService`, and chamber trackers.
- Produces: `daily_report.txt` written through `ReportStoragePort`.

- [ ] **Step 1: Write test in `ReportServiceTest.java` comparing generated report text against Golden Master report format**

- [ ] **Step 2: Run `mvn test -Dtest=ReportServiceTest` to verify failure**

Expected: FAIL.

- [ ] **Step 3: Implement Template Method and Storage Adapter**

- `AbstractReportGenerator`: defines template algorithm `generate()` (`extractMetrics()` $\to$ `formatHeader()` $\to$ `formatBody()` $\to$ `save()`).
- `PlainTextReportFormatter`: generates exact lines matching legacy `daily_report.txt`.
- `FileReportStorageAdapter`: uses Java NIO to write to `archive/daily_report.txt`.

- [ ] **Step 4: Run `mvn test -Dtest=ReportServiceTest` to verify pass**

Run: `mvn test -Dtest=ReportServiceTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/service/report/ src/main/java/org/example/svfrms/infrastructure/storage/ src/test/
git commit -m "feat: implement GoF Template Method report generator and file adapter"
```

---

### Task 9: Composition Root, Application Context & Main Simulation Bootstrap

**Files:**
- Create: `src/main/java/org/example/svfrms/infrastructure/context/SvfrmsApplicationContext.java`
- Create: `src/main/java/org/example/svfrms/concurrency/ChamberSimulationTask.java`
- Create: `src/main/java/org/example/svfrms/SvfrmsApplication.java`
- Modify: `src/main/java/org/example/SVFRMS.java` (delegate legacy entry point to modernized context)
- Create: `src/test/java/org/example/svfrms/FullSystemIntegrationTest.java`

**Interfaces:**
- Wires all subsystems together using Pure Dependency Injection.
- Preserves exact entry point `org.example.SVFRMS.main(String[] args)`.

- [ ] **Step 1: Write `FullSystemIntegrationTest.java`**

Bootstraps the entire system, executes 4 chambers across 20 iterations each, runs report and archive pipelines, and validates zero regressions.

- [ ] **Step 2: Run `mvn test -Dtest=FullSystemIntegrationTest` to verify failure**

Expected: FAIL.

- [ ] **Step 3: Implement `SvfrmsApplicationContext`, `ChamberSimulationTask`, and `SvfrmsApplication`**

- `SvfrmsApplicationContext`: instantiates registries, meters, queues, services, and wires dependencies via constructors.
- `ChamberSimulationTask`: runnable task executing 20 sensor iterations for a given chamber, submitting readings to `TelemetryPublisher`.
- Spawns chamber tasks using Java 25 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
- Refactor `SVFRMS.java` to act as a lightweight facade delegating directly to `SvfrmsApplication.main(args)`, preserving backward compatibility.

- [ ] **Step 4: Run full test suite including Golden Master**

Run: `mvn test`  
Expected: ALL TESTS PASS (100% green, Golden Master passes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/svfrms/ src/main/java/org/example/SVFRMS.java src/test/
git commit -m "refactor: complete modular decomposition and wire application context"
```

---

### Task 10: Section 4.5 Feature Extensions & Executable JAR Packaging

**Files:**
- Modify: `pom.xml` (configure `maven-shade-plugin` or `maven-assembly-plugin` to build `run.jar`)
- Create: `src/test/java/org/example/svfrms/FeatureExtensionCropTest.java`

**Interfaces:**
- Produces: Verified addition of **Kale**, **Bell Pepper**, **Bok Choy**, **Eggplant**, and crop removal.
- Produces: Executable `target/run.jar` and root `run.jar`.

- [ ] **Step 1: Write test in `FeatureExtensionCropTest.java`**

Register Kale, Bell Pepper, Bok Choy, and Eggplant; trigger out-of-range sensor readings; assert that specific alerts and actuator commands fire correctly. Test dynamic de-registration.

- [ ] **Step 2: Run `mvn test -Dtest=FeatureExtensionCropTest` to verify pass**

Run: `mvn test -Dtest=FeatureExtensionCropTest`  
Expected: PASS (OCP crop extension verified).

- [ ] **Step 3: Configure `pom.xml` for executable `run.jar`**

Configure `maven-shade-plugin` with `finalName` set to `run` and manifest pointing to `org.example.SVFRMS`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <finalName>run</finalName>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>org.example.SVFRMS</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Build package and verify executable JAR**

Run: `mvn clean package`  
Run: `java -jar target/run.jar`  
Expected: Executes cleanly, outputs expected logs, generates `archive/daily_report.txt`, exits with code 0.

- [ ] **Step 5: Copy `target/run.jar` to root `run.jar` and commit**

```bash
cp target/run.jar run.jar
git add pom.xml run.jar src/test/java/org/example/svfrms/FeatureExtensionCropTest.java
git commit -m "feat: complete Section 4.5 crop extensions and build standalone executable run.jar"
```

---

## Plan Self-Review Checklist

- [x] **Spec Coverage**: Every requirement from Sections 3, 4 (4.1–4.5), 5, 6, and 7 of `se301_project_specification.md` has a dedicated implementing task.
- [x] **Step Scan**: Every step carries an exact signature, test, or checkable command (`mvn test -Dtest=...`).
- [x] **Type Consistency**: Domain record and service names (`ChamberSnapshot`, `SensorReading`, `DeviceCommand`, `CropRegistry`) are consistent across Tasks 2 through 10.
- [x] **Review Focus**: Edge cases (lost updates, phase races, queue deadlocks, output drift, dynamic extensions) have specific verification tests in Tasks 1, 4, 5, 7, and 10.
- [x] **Proportion**: Focused design specifications with bite-sized tasks rather than bloated code dumps.
