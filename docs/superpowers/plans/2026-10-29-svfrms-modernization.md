# SVFRMS Behavior-Preserving Remediation Implementation Plan

> **For agentic workers:** Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` when implementation begins. Complete the acceptance criteria before marking a task done. This document supersedes the earlier modernization plan at this path.

**Goal:** Repair the existing implementation while preserving the supplied application's functionality and supporting the crop extensibility required by Section 4.5.

**Architecture:** Retain the component layout, constructor injection, existing telemetry/strategy/command boundaries, and Java SE concurrency primitives. Extend working components incrementally; do not replace the application or introduce unused patterns.

**Tech stack:** Java 25, Maven, existing JUnit 5/AssertJ dependencies, and the existing executable-JAR packaging.

**References:** [Project specification](../../se301_project_specification.md), [research synthesis](../../RESEARCH_SYNTHESIS.md), and the six documents in [research](../../research/).

**Source of truth:** Original source at commit `a2ed2ed3266e1f676c0528515b52c1a33f2fabc8`, path `src/main/java/org/example/SVFRMS.java`. Remediate the implementation committed as `ab0c1d8` on `refactor/svfrms-modernization-gpt`.

## Confirmed Scope and Execution Rules

- The user selected preservation of the supplied code's functionality. Do not add missing Section 3 features, production crop presets, new alert/severity policies, agricultural thresholds, report types, or frameworks.
- Section 4.5 extensibility is verified using test-only copies of existing profiles under additional names. Do not register those test profiles in the production simulation.
- Follow the specification and original source over conflicting research recommendations. Preserve callable workflows, including manual cycles and explicit log/alert display.
- Preserve deterministic output syntax, strict thresholds, resource charges, actuator formulas, sensor-log retention, and report contents. Correct unsafe concurrent updates and explicitly order reporting/archival rather than reproducing scheduling bugs.
- Do not add physical-range validation or change unknown-name behavior without evidence from the original reference. Characterize exceptional numeric inputs before altering their handling.
- Work incrementally: add a targeted regression assertion, observe its failure when behavior is missing, make the narrow fix, then run the relevant tests. Existing correct components need verification rather than replacement.
- No implementation is marked complete by this plan. All tasks below remain subject to their acceptance evidence.

## Legacy Compatibility Matrix

Every condition below generates an alert. Equality to the threshold does not trigger it. Resource charges occur at alert evaluation; direct control activation does not charge resources.

| Crop / sensor | Low condition and action | High condition and action | Alert resource charge |
|---|---|---|---|
| Tomato temperature | `<22`: Low Temp, no command | `>28`: High Temp, Cooling Fan | Low +15 energy; high +10 energy |
| Strawberry temperature | `<18`: Low Temp, no command | `>24`: High Temp, Cooling Fan | Low +15 energy; high +10 energy |
| Lettuce temperature | `<15`: Low Temp, no command | `>21`: High Temp, Cooling Fan | None |
| Spinach temperature | `<10`: Low Temp, no command | `>20`: High Temp, Cooling Fan | None |
| Chili temperature | `<24`: Low Temp, no command | `>32`: High Temp, Cooling Fan | None |
| Cucumber temperature | `<18`: Low Temp, no command | `>27`: High Temp, Cooling Fan | None |
| Tomato humidity | `<60`: Low Humidity, Humidifier | `>80`: High Humidity, Dehumidifier | Low +20 water; high none |
| Strawberry humidity | `<70`: Low Humidity, Humidifier | `>85`: High Humidity, Dehumidifier | Low +25 water; high none |
| Tomato soil moisture | `<45`: Low Soil Moisture, Water Pump | No high rule | Low +50 water |
| Any crop, including unknown/removed: light | `<500`: Low Light Level, Grow Light | No high rule | None |
| Any crop, including unknown/removed: nutrient | `<40`: Low Nutrient Level, Nutrient Injector | No high rule | None |

Humidity for other legacy crops and soil for crops other than Tomato only update chamber state and store sensor logs. Crop matching remains case-sensitive. Alert messages include the chamber prefix and, for temperature/humidity/soil, the crop name. Light/nutrient messages omit the crop name. Preserve the original console wrappers and ANSI control lines exactly.

Initial chamber state is temperature 24, humidity 70, soil 60, light 600, and nutrient 70. Initial summary messages are `No action` and `None`.

| Device | Exact update applied to current state at execution |
|---|---|
| Cooling Fan | `max(18, temperature - 5)` |
| Water Pump | `min(100, soilMoisture + 20)` |
| Nutrient Injector | `min(100, nutrientLevel + 15)` |
| Humidifier | `min(100, humidity + 10)` |
| Dehumidifier | `max(30, humidity - 10)` |
| Grow Light | `min(1000, lightLevel + 150)` |

Each completed simulation iteration additionally charges 2 water and 1 energy. The original four chambers and twenty iterations therefore contribute 160 water and 80 energy before alert charges. Manual `irrigationCycle()` adds 100 water; `feedingCycle()` adds 20 energy.

## Implementation Sequence

### Agent Execution Protocol

Paths below use `M = src/main/java/org/example/svfrms/` and `T = src/test/java/org/example/svfrms/`. These are path abbreviations, not new source directories. Execute Tasks 1 -> 2 -> 3 -> 4; update every caller of an interface in the same task that changes it.

For each numbered task, perform these steps in order:

1. Read its named files and the pinned original methods. Identify which acceptance conditions already hold; retain those implementations.
2. Add the named tests/assertions below. Run the task's focused command and observe the relevant assertion fail for a missing behavior. A compile error is only an intermediate result while introducing a new API, not the regression evidence. Do not weaken expectations to preserve current incorrect behavior.
3. Apply the task's implementation checklist. Keep interface migrations and their dependent caller/test updates together. Add no production feature to make a test easier.
4. Re-run the focused command, then `mvn test`. The gate is zero failures/errors, no unexplained skipped tests, and all task-specific assertions satisfied. A passing test with no meaningful assertions does not count. If behavior already passes its new regression test, record that evidence without manufacturing a defect.
5. Record changed files, test names, exact commands, and results in `docs/superpowers/plans/2026-10-29-svfrms-remediation-evidence.md`. Mark only verified checkboxes. Keep commits task-scoped if committing is requested; never stage unrelated working-tree changes.

Task 1 establishes reference fixtures independently of the modern implementation. Modern parity assertions that expose unresolved defects are introduced with their owning remediation task, so a knowingly broken default suite is not handed to the next task.

### Task 1: Establish an Independent Legacy Reference

Primary owner: existing `GoldenMasterLegacyTest`, supported by a test-only reference harness and frozen original source under `src/test/resources/legacy/`.

**Files:** Modify `T/GoldenMasterLegacyTest.java`; create `T/LegacyReferenceHarness.java`, `src/test/resources/legacy/SVFRMS.java.txt`, `src/test/resources/legacy/README.md`, and checked-in expected-output fixtures in that resource directory. Use the JDK compiler; do not add a runtime framework or mocking agent.

**Named tests and exact assertions:**
- `referenceSourceIsPinnedOriginal`: the resource bytes match the pinned Git blob after the explicitly recorded checkout line-ending convention; the reference JVM loads the compiled original, not the modern classpath.
- `referenceScriptProducesStableFixtures`: two fresh reference processes produce identical normalized outputs and all state/counter results for the same script.
- `goldenMasterDetectsThresholdAndCostDrift`: modifying Tomato high-temperature threshold 28 or charge 10 causes the relevant comparison to fail; restore the mutation before accepting the task.

**Focused command:** `mvn test "-Dtest=GoldenMasterLegacyTest"`.

**Acceptance gate:** Frozen original, complete fixtures, and their provenance exist; deterministic reference assertions pass. The fixture README enumerates every permitted normalization and the script's command-draining/report ordering. Add modern mismatch assertions with Tasks 2–4, not by regenerating fixtures from modern output.

- [x] Freeze the pinned original source as a test resource; compile it separately and execute scripted sensor/control operations in an isolated JVM and temporary working directory. Never resolve reference calls to the modern facade.
- [x] Capture complete console output, stored logs, report contents, chamber states, alert/action counts, and resource totals. Drain controls after each scripted reading to control execution order and avoid filling the original queue.
- [x] Normalize only declared timestamp fields and the absolute report path. Pin timezone and line endings; assert exact modern timestamp rendering separately with an injected fixed clock. Do not normalize alert text, amounts, ANSI escapes, or arbitrary ordering.
- [x] Cover every compatibility-matrix branch, equality and just-outside boundaries, disabled checks, unknown/case-mismatched names, direct controls, repeated commands at clamps, and empty archival.
- [x] Verify that a temporary threshold/cost mutation fails parity, then restore it. Add regression assertions before each subsequent fix and retain currently correct behavior.

### Task 2: Correct Crop Evaluation and Accounting

Primary owners: existing crop profiles/registry, threshold strategy, telemetry evaluation observer, and resource accounting service.

**Files:** Modify `M/domain/crop/{CropProfile,CropRegistry,MitigationResourceCosts}.java`, `M/service/crop/{CropThresholdStrategy,DefaultCropThresholdStrategy,EvaluationResult}.java`, `M/service/telemetry/ThresholdEvaluationObserver.java`, `M/service/resource/ResourceAccountingService.java`, and `M/concurrency/ChamberSimulationTask.java`. Update `M/command/{CoolingFan,WaterPump,Humidifier}Command.java` now to remove command-time charges; Task 3 changes the command signature afterward. Extend `T/domain/crop/CropRegistryTest.java`, `T/service/resource/ResourceAccountingServiceTest.java`, and `T/FeatureExtensionCropTest.java`; create `T/LegacyCompatibilityTest.java`.

**Interface contract:** `CropProfile` gains defensively copied `Set<SensorType> enabledSensors` and builder method `enabledSensors(Set<SensorType>)`. Standard profiles explicitly enable their matrix sensors. Existing range/cost builder APIs remain; their default enabled set includes all existing sensor types, while baseline profiles override it for intentionally absent checks. `CropThresholdStrategy.evaluate(SensorReading, CropProfile)` keeps returning `EvaluationResult`; add `evaluateWithoutProfile(SensorReading) -> EvaluationResult` for lower-only universal light/nutrient evaluation. No additional thresholds or mitigation types are introduced.

**Named tests and exact assertions:**
- `legacyMatrixMatchesReference`: parameterize every matrix rule at its threshold, `Math.nextDown(threshold)`, and `Math.nextUp(threshold)`; assert complete alert text, optional actuator, and both resource deltas.
- `disabledSensorsOnlyUpdateStateAndLog`: Lettuce humidity=-1 and Strawberry soil=-1 generate no alert, command, or charge, but the measured field and stored log update.
- `unknownAndCaseMismatchedCropsKeepUniversalChecks`: `Unknown` and `tomato` do not enable Tomato temperature rules; light=499 and nutrient=39 still trigger their original global alerts.
- `chargesExistBeforeConsumerRuns`: pause consumption, publish Tomato temperature=29, and assert energy=10 before the fan runs and energy=10 after it runs. Tomato temperature=21 yields energy=15 and no command; direct fan activation charges zero.
- `simulationAndManualCycleTotalsAreExact`: assert 160/80 base totals after all 4x20 iterations join, add independently calculated alert deltas, and assert manual cycles contribute exactly +100 water/+20 energy.
- `replacementAndRemovalUseOneProfilePerReading`: pause after lookup, replace/remove the profile, then release evaluation; assert the old in-flight thresholds/costs stay paired, accepted work finishes, later lookups use replacement/fallback, and history remains intact.

**Focused command:** `mvn test "-Dtest=CropRegistryTest,ResourceAccountingServiceTest,FeatureExtensionCropTest,LegacyCompatibilityTest"`.

**Acceptance gate:** Every matrix branch and disabled branch matches the pinned reference; no duplicate meter increments remain. The same running engine accepts test-profile registration/removal without source edits or production preset additions.

- [x] Keep existing profile ranges and cost data authoritative. Add an immutable `Set<SensorType> enabledSensors` to represent disabled checks explicitly. Remove the earlier proposal for a separate `ThresholdRule` map: there must not be two independently editable rule representations.
- [x] Baseline profiles explicitly enable the checks in the compatibility matrix. The strategy retains two-sided temperature/humidity and lower-only soil/light/nutrient behavior. Registry/builder data owns crop differences; add no crop-name branches to the engine.
- [x] Restore case-sensitive matching. For unknown/removed profiles, still update state/store logs and evaluate the original universal light/nutrient checks.
- [x] Fetch one immutable profile per reading. Registration replaces it atomically; in-flight evaluation uses that one profile for both thresholds and costs. Removal affects subsequent lookups, preserves history, and does not cancel already accepted controls.
- [x] Record the `EvaluationResult` charge exactly once during alert evaluation, before mitigation submission, including low-temperature alerts without commands. Submission failure retains the alert/charge and surfaces a failed run; it does not count a successful mitigation.
- [x] Keep `AlertManagementService` authoritative for alert counts. Remove duplicate alert counters from resource accounting and update all consumers together.
- [x] Preserve per-iteration and manual-cycle accounting. Verify runtime registration/removal with test-only copies of existing profiles under new names; add no production presets or new agricultural assumptions.

### Task 3: Preserve State, Control, and Public Workflows

Primary owners: chamber trackers, concrete commands, telemetry logging, application context, and the `org.example.SVFRMS` facade.

**Files:** Modify `M/domain/chamber/{ChamberStateTracker,ChamberSummaryTracker}.java`, the existing classes under `M/command/`, `M/service/actuator/ActuatorExecutionService.java`, `M/service/telemetry/{ConsoleLoggingObserver,ThresholdEvaluationObserver}.java`, `M/infrastructure/context/SvfrmsApplicationContext.java`, and `src/main/java/org/example/SVFRMS.java`. Extend `T/domain/chamber/ChamberStateTrackerTest.java`, `T/service/actuator/ActuatorServiceTest.java`, and `T/service/telemetry/TelemetryPublisherTest.java`; create `T/LegacyOperationsTest.java`.

**Interface contract:** `DeviceCommand.execute(ChamberStateTracker tracker)` returns `ChamberSnapshot`; callers format `stateDescription` from that returned snapshot. `ChamberStateTracker.updateAndGet(UnaryOperator<ChamberSnapshot>)` retains its signature and pure callbacks. Facade methods retain their original names/parameter lists for `processSensor`, `activateControl`, `addControlRequest`, `handleEvent`, `showLogs`, `showAlerts`, `irrigationCycle`, `feedingCycle`, `generateDailyReport`, `archiveAlerts`, and `saveArchiveReport`. `getChamberState(String)` and `getChamberSummary(String)` return immutable snapshots, not mutable legacy inner objects. Document this source-level accessor migration rather than claiming binary compatibility.

**Named tests and exact assertions:**
- `sameChamberHasOneTrackerUnderConcurrentCreation`: simultaneous requests for the same chamber obtain the identical tracker object.
- `sensorUpdatesPreserveConcurrentControlFields`: interleave temperature-only sensor updates with 100 pump updates from soil=60; assert soil=100 and the last committed temperature, with no unrelated field resets.
- `retryableStateUpdatesDoNotRepeatSideEffects`: force a CAS conflict with barriers; assert one external summary increment/output event per completed operation, regardless of callback retries.
- `commandsPreserveLegacyClampsAndOutput`: verify all six formulas below/at/above clamps, repeated activations, exact two ANSI lines, one action-summary increment, unchanged other fields, and zero direct-command charges. Verify the description uses the committed result even if a later sensor update occurs.
- `sensorLogsAreStoredUntilExplicitDisplay`: publishing a reading stores one exact legacy-labelled log and emits no sensor stdout; `showLogs()` subsequently prints the stored entries in order.
- `legacyFacadeOperationsMatchReference`: invoke every listed operation, all six event types, unknown names, a new chamber ID, and a fresh second session. Assert original state/output/accounting effects with no leakage across sessions. Verify `CREATE_ALERT` and `TRACK_ALERT` retain distinct effects rather than double-counting summaries.

**Focused command:** `mvn test "-Dtest=ChamberStateTrackerTest,ActuatorServiceTest,TelemetryPublisherTest,LegacyOperationsTest,LegacyCompatibilityTest"`.

**Acceptance gate:** All original callable workflows are reachable; sensor/control changes are atomic; log/state/summary counts are exact after writers join; baseline output matches the reference under a controlled schedule.

- [x] Ensure readings and commands share the same safely initialized chamber trackers, using concurrent-map atomic initialization rather than copying an empty map before later registration.
- [x] Sensor updates preserve unrelated fields. CAS callbacks transform state only; logging, accounting, queue submission, and summary changes occur outside retryable callbacks.
- [x] Atomically update summary counts and associated last messages together.
- [x] Change the internal command contract to `execute(ChamberStateTracker) -> ChamberSnapshot`; remove its accounting dependency and update concrete commands/consumer/tests together. Control output uses the returned committed snapshot.
- [x] Preserve all device formulas, messages, and one summary increment per successful command. Direct activation changes no resource meter.
- [x] Store sensor logs for explicit `showLogs()` display; do not print readings automatically. Preserve original sensor labels, including `Soil`.
- [x] Restore facade operations for sensor processing, direct/queued control, the six known event-dispatch branches, explicit log/alert display, manual cycles, daily report generation, archival, and saving. Preserve unknown sensor/control/event no-ops and the original unknown-sensor log/state-initialization effects.
- [x] Keep state encapsulated. Expose immutable inspection snapshots instead of writable global collections; document accessor return-type changes. Main creates a fresh session per run; compatibility operations share a lazily owned session with explicit cleanup and test reset.

### Task 4: Finish Concurrency and Lifecycle Fixes

Primary owners: actuator queue/worker, alert management, report coordination, and application lifecycle.

**Files:** Modify `M/concurrency/{BoundedControlQueue,ActuatorRequest}.java`, `M/command/StopActuatorCommand.java`, `M/service/actuator/ActuatorExecutionService.java`, `M/service/alert/{AlertManagementService,AlertSnapshot}.java`, `M/service/report/{DailyReportService,DailyReportSnapshot}.java`, `M/service/telemetry/TelemetryPublisher.java`, `M/infrastructure/context/SvfrmsApplicationContext.java`, and `M/SvfrmsApplication.java`. Extend `T/service/{actuator/ActuatorServiceTest,alert/AlertManagementServiceTest,report/ReportServiceTest}.java` and `T/FullSystemIntegrationTest.java`; create `T/concurrency/ApplicationLifecycleTest.java`.

**Interface contract:** Preserve `submit(ActuatorRequest, Duration) -> boolean throws InterruptedException` and `close() throws InterruptedException` on the actuator service; add `failure() -> Optional<Throwable>`. Admission after closing/failed state throws `IllegalStateException`; a full-queue timeout returns false and the telemetry caller fails visibly. `AlertManagementService.snapshot() -> AlertSnapshot` and `archiveAlerts() -> List<AlertEvent>` share the publication lock. Required telemetry-observer failure reaches the producer future; logging the exception alone is insufficient.

**Named tests and exact assertions:**
- `alertSnapshotsNeverObservePartialArchival`: give alerts unique test messages and coordinate publishers/readers/archivers; every snapshot satisfies generated=active+archived with no duplicate membership, and final archival contains every published alert exactly once.
- `fullQueueTimesOutAndLateAdmissionIsRejected`: use capacity one and a latch-paused consumer; assert false on timeout, surfaced producer failure, and rejection after close begins.
- `throwingCommandAndOutputFailureRemainObservable`: follow a throwing command with a valid command; assert the later work finishes, the first failure remains recorded even if its output callback throws, and the application cannot report normal success.
- `closeDrainsAcceptedCommandsExactlyOnce`: race submit/close and account for every accepted request as executed or explicitly failed; no accepted work follows the stop signal and no worker survives successful close.
- `producerFailureAndInterruptionAlwaysCleanUp`: inject each failure before reporting, cancel sibling production, verify bounded shutdown and interrupt handling, and assert no normal completion line.
- `realShutdownHookFinishesWithinBudget`: the test-only child runner triggers JVM shutdown while work is active; verify hook completion marker and timely exit, reaping the child in `finally`.
- `reportSnapshotsRespectPhaseOrdering`: with N distinct alerts, assert console active=N, file active=0/archived=N/generated=N, identical resource totals, and history `Daily Report` before `Archived N alerts`. Cover N=0 and repeated archival.
- `storageFailurePreservesLegacyMessage`: a failing adapter produces the original error line and no saved-success line; successful writes create directories, truncate stale content, and preserve UTF-8/report syntax.

Use latches/barriers for interleavings, not sleeps. Inject a one-second shutdown budget in focused lifecycle tests and an outer ten-second test timeout; release all latches and terminate test executors in `finally`. Main uses the ten-second production cleanup budget, with a thirty-second outer subprocess timeout for the complete smoke run. Test failure must not leave a blocked thread or child process behind.

**Focused command:** `mvn test "-Dtest=ActuatorServiceTest,AlertManagementServiceTest,ReportServiceTest,TelemetryPublisherTest,ApplicationLifecycleTest,FullSystemIntegrationTest"`.

**Acceptance gate:** Snapshot invariants and all failure tests pass; normal completion drains accepted work before reporting; interrupted/failed runs surface failure and never claim successful normal completion. Then run the full packaging gates below.

- [x] Retain one alert-service lock covering publication, generated count, archival transfer, and snapshot capture. Derive active/archive counts from immutable snapshot lists. `List.copyOf` is not the synchronization mechanism.
- [x] Restore queue capacity five, with constructor-injected smaller capacity for tests. Preserve ordered execution; expose timeout/interruption to callers instead of silently dropping work.
- [x] Coordinate admission with closing so no accepted request follows the stop signal. Do not hold a lock required by the consumer while waiting for queue space or joining it.
- [x] Record recoverable command failures independently of the output callback and continue draining later accepted work. Surface failures to the application; do not retry potentially partial effects or report discarded work as successfully completed.
- [x] Observe producer futures, cancel sibling production on failure, and always clean up workers through `finally`. Stop producers before closing actuator admission.
- [x] Order normal completion: producers finish -> stop admission -> drain controls -> join consumer -> print daily report -> archive alerts -> print completion -> save archive report. Console active count is pre-archive; the file count is post-archive. Preserve generated/resource totals and report-history ordering.
- [x] Use one ten-second cleanup budget across shutdown phases, with shorter injected test budgets. Close is idempotent and restores/propagates interruption appropriately. Hook and main share the cleanup outcome; report timeout or non-cooperative worker failure honestly.
- [x] Exercise the actual shutdown hook with a test-only child-process runner whose stdin shutdown command invokes `System.exit`; production gains no exit command. Reap child processes on timeout.
- [x] Suppress normal completion messages after worker failure/incomplete shutdown. Preserve original report-storage failure text and avoid a saved-success line after a failed write.

## Test and Acceptance Plan

| Area | Required proof |
|---|---|
| Legacy parity | Every original alert branch, boundary, disabled check, charge, unknown-name behavior, callable operation, control clamp, and report section |
| Accounting | 160 water/80 energy base usage for 4x20 completed iterations, plus exact alert charges; no command-time duplicate charges |
| State and summaries | Concurrent tracker initialization, unrelated-field preservation, exact effect counts, committed-snapshot control output, coherent summary updates |
| Alerts | Publication/archive/snapshot interleavings preserve `generated = active + archived`; no missing/duplicated alerts; old snapshots stay unchanged |
| Liveness | Full queue, throwing command, failed output callback, producer failure, interruption, late submission, repeated close, and real-hook execution, using latches and bounded waits |
| Crop lifecycle | Same engine handles registration/removal/replacement; accepted controls finish; history survives; unknown/removed crop fallback matches the original |
| Reporting | Console pre-archive/file post-archive counts, unchanged syntax/history/ordering, UTF-8 create/truncate behavior, and original error handling |
| Packaging | Executable manifest, successful normal exit, report creation, and JAR execution from a fresh working directory without IDE settings |

- [x] Run `mvn clean test`, then `mvn package`, then `java -jar target/run.jar`; inspect exit codes and generated report contents. No unexplained skipped suites.
- [x] Run the packaged JAR from a fresh temporary directory. Confirm expected local archive creation and timely termination.
- [x] Request an independent final review against the original source, specification, and this plan. Resolve findings before claiming remediation complete.

## Documentation and Completion

- [x] Correct conflicting recommendations in research documents 01, 03, 05, and 06 and the synthesis: feature deletion, expanded crop checks, standardized charges, atomic-snapshot claims, deadlock attribution, and unsupported performance figures.
- [x] Explain SRP/OCP/DIP using actual boundaries and demonstrate LSP/ISP through consistent, focused command/storage contracts. Add no interfaces or patterns solely for grading. Do not equate immutability with thread safety or substitutability.
- [x] Document the selected preservation-first interpretation and Section 3 capabilities absent from the original. Do not claim those capabilities were implemented.
- [x] Produce the required 3-6-page report body, cover page, component diagram, and submission checklist. Keep `README.pdf` canonical and include an identical `report.pdf` copy to satisfy both names in the specification.
- [ ] Use actual group/member identities; missing details remain explicitly pending. Prepare the five-minute presentation/two-minute demo around verified behavior and a test/demo profile copied from an existing profile.
- [x] Assemble current source, `pom.xml`, verified root `run.jar`, and reports in the submission ZIP. Do not mistake the existing `src.zip` for an automatically current submission. Verify extracted contents and executable behavior.
- [x] Record completed fixes, test evidence, remaining artifacts, and any blockers separately. Passing the existing tests alone does not establish completion of these remediation criteria.

## Execution Outcome (2026-10-29)

Tasks 1-4 implemented and independently reviewed. Final suite: 185 tests passing; package and root smoke pass. Draft report and submission bundle generated. Actual cover identities and human rehearsal remain pending; this is not a submission-ready claim. See the [evidence record](2026-10-29-svfrms-remediation-evidence.md).
