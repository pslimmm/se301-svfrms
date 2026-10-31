# SVFRMS FINALIZED Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (recommended native implementation) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. User approved execution on31 October2026. Native sequential execution is in progress with a final independent review.

**Goal:** Consolidate compatible improvements into `refactor/svfrms-modernization-FINALIZED`, preserve legacy behavior, correct reproduced defects and prepare verifiable final deliverables.

**Architecture:** Continue the GPT branch's facade/context/component design with one authoritative state/lifecycle graph. Repair report persistence/presentation, share command construction and log ownership, adapt donor verification/demo/documentation, and retain the independent original oracle. Avoid merging competing engines or enabling new default policies.

**Tech Stack:** Java SE25, Maven, current JUnit5/AssertJ test dependencies, existing Maven plugins; JaCoCo0.8.14 test-only coverage plugin/profile. Document/slides tooling is authoring-only and must not become an application runtime dependency.

**Spec:** `docs/superpowers/specs/2026-10-31-svfrms-finalized-design.md`; `docs/finalization/constraint-matrix.md`; authoritative attached PDF and matching user-linked Markdown. Read all three before execution.

## Global Constraints

- Specification: “The goal is not to change system functionality.” Preserve behavior, output formats, alert calculations and all callable sensor/control/manual/display/report workflows; no wholesale replacement or removed features.
- Specification: “A submission that remains in a single file will be disqualified.” Retain focused component packages under `src/main/java/`.
- Specification: “Java 25 or later”; “Maven permitted”. Build `target/run.jar`; run `java -jar target/run.jar`; no application framework or preview requirement.
- Specification: “3 - 6 pages, excluding the cover page.” Retain the 3-6 body-page target, real group/member cover and legible architecture diagram. Per the user's clarified instruction, deliver README.docx and identical report.docx instead of PDFs. Record the specification's PDF filename/format requirement as a disclosed user-directed format deviation; do not certify that requirement as satisfied.
- Use exact branch **`refactor/svfrms-modernization-FINALIZED`**. No auto-merge, push, eLearn upload, identity invention or claim of human rehearsal/submission.
- Crop add/remove requires zero engine/evaluator edits. Preserve existing six crop policies and unknown/removed-crop light<500/nutrient<40 fallback.
- Use owned concurrency primitives, real JVM hook, failure supervision and accepted-work drain. Never hold state locks across queue waits, external callbacks, disk I/O or worker joins.
- Source of behavior: original `a2ed2ed3266e1f676c0528515b52c1a33f2fabc8:src/main/java/org/example/SVFRMS.java`, not donor refactors or horticultural assumptions.
- C/S requirement rows are global gates. HUMAN-PENDING is never converted to PASS without factual evidence. User-confirmed Section3 omissions are DOCUMENTED-GAP, not implemented features; this plan preserves scope and discloses them.
- Run `mvn clean test`, `mvn package`, `java -jar target/run.jar` after every completed code/refactoring task. Additional coverage and ZIP checks do not replace these commands.

## Review Focus

1. Two complete reports saved simultaneously to the same destination, including separate adapter instances: final file is one whole candidate, never a hybrid (Task3).
2. Mid-write failure, unsupported atomic move and real replacement failure: previous file and legacy-facing error behavior remain correct; temporary files are cleaned (Tasks3/9).
3. Daily output paused while a concurrent archive starts: a contiguous report block, coherent helper ordering and callback getters without state-lock deadlock (Task4).
4. Close on a real positive backlog/full queue, consumer failure or JVM shutdown: every accepted request executes once or has a visible failure; no false-success completion (Tasks2/7).
5. Profile replacement/removal during evaluation and exceptional raw sensor inputs: one immutable profile per reading; exact original rule/cost/NaN/infinity/unknown-name behavior (Tasks2/6).

## File and responsibility map

Paths below are repository-relative execution targets; do not edit `.review` snapshots as implementation.

| File(s) | Responsibility/change |
| --- | --- |
| `.gitignore`, `README.md`, `docs/finalization/{constraint-matrix.md,requirements.json,branch-provenance.md,verification.md}` | Track curated handoff evidence and maintain true compliance/provenance |
| `pom.xml`, `scripts/{review_branch_baselines.py,verify_coverage.py}` | Pin build/coverage configuration; collect isolated baseline evidence; enforce measured coverage |
| `.../infrastructure/storage/FileReportStorageAdapter.java` | Whole-report sibling-temp replacement; deterministic NIO fault seam |
| `.../service/report/DailyReportService.java` | Serialize report/archive presentation/history without state-lock I/O |
| `.../command/DeviceCommandFactory.java`, context, threshold observer | One six-command mapping, with existing facade selectors retained |
| `.../service/telemetry/SensorLog.java`, context | Owned append/snapshot log preserving order and retention |
| `.../demo/CropExtensionDemo.java` | Demonstrate actual runtime crop addition/removal through existing services |
| `src/test/java/org/example/svfrms/...` | New storage/order/demo tests; adapted donor boundary/lifecycle regressions; retain original oracle |
| `docs/{technical-report.docx,technical-report.md,report-metadata.json,submission-checklist.md,demo-script.md}`, `docs/diagrams/*`, `docs/presentation/*` | Reproducible factual report/diagrams/deck/rehearsal material |
| `scripts/{build_report.py,package_submission.py,verify_submission.py}`, `submission/SVFRMS-FINALIZED.zip` | Render/report/package current files; verify exact extracted deliverables |

`...` above denotes `src/main/java/org/example/svfrms/`; full paths are named in tasks. Do not add a second runtime context, registry or lifecycle implementation.

## Task 1: Establish branch, scope ledger and reproducible donor baseline

**Files:** modify `.gitignore`; create `docs/finalization/requirements.json`, `branch-provenance.md`, `scripts/review_branch_baselines.py`; retain this design/plan/ledger and audit.

**Interfaces:** script `python scripts/review_branch_baselines.py --output target/branch-baselines` creates isolated archives/build directories and JSON/log evidence for exactly four refs; no checkout/reset/donor edits. `requirements.json` follows the ledger schema. No runtime changes.

- [x] Check `git status --short`, current branch/HEAD, and remote branch existence. Preserve local work. Refresh refs; if any source SHA differs from the reviewed table, inspect its delta and rerun its audit before updating pins. If a FINALIZED branch exists, do not force/reset it; inspect it and resolve reuse explicitly.
- [x] In a suitable clean checkout, run `git switch -c refactor/svfrms-modernization-FINALIZED fe7b6d27cc9f674bfec352cae55afea58a440a05`. If isolation is needed, use `superpowers:using-git-worktrees` and the app's native worktree tool. Confirm exact branch and `git merge-base --is-ancestor fe7b6d27cc9f674bfec352cae55afea58a440a05 HEAD` exits0. Never substitute the default codex/ prefix for the user's requested name.
- [x] Record four pins: modernization `3fe2d121ed95cfa1f5d5507fc1590f0617c39b90`; Gemini `81cc18ea152244bb40297b183e5f0ca728673b03`; GPT `fe7b6d27cc9f674bfec352cae55afea58a440a05`; Astra `9f23b6cf8e8030681c2a119f0233cb18bd35d643`. Treat the local research as untracked external audit material, not Gemini commit contents.
- [x] Track only curated docs/scripts by fixing relevant `.gitignore` rules. Keep raw `.review`, lecture PDFs, build logs, target/ and generated sensor archives out. Verify `git check-ignore` no longer hides the design/plan/report source/checklist/demo/provenance files; do not blanket add ignored private files.
- [x] Implement the baseline script using archive snapshots and bounded subprocesses. For each ref run the three mandated commands, capture exits/test counts, and record Astra's expected missing target/run.jar failure plus its separately named runnable artifact. Stop only script-owned subprocesses on timeout. Current unchanged pins previously passed49/29/185/300 tests; these are expected observations to reverify, not hardcoded success.
- [x] Create adopt/adapt/reject rows for every finding in the audit. Proposed adoption: GPT engine/lifecycle/oracle; modernization golden scenarios/demo/diagrams; Astra deterministic failure/boundary/packaging ideas; Gemini CAS/registration scenarios and its defects as negative tests. Reject new default crop checks, disconnected state, lost offer results, unqualified snapshots, stale binaries and cosmetic pattern multiplication.
- [x] Record the user-confirmed Section3 decision: preserve existing scope and document the mismatch. Set the three omitted-feature rows to DOCUMENTED-GAP. Record group3; member names are deferred, so leave editable fields blank and do not claim the final cover is complete. Commit the branch setup and curated planning/evidence files with an explicit path list.

**Acceptance:** exact new branch, no donor/worktree damage, all four baseline results and decisions retained, documentation is actually tracked. Tasks3-5 can execute after design approval independent of unresolved human cover data; final release cannot.

## Task 2: Preserve the oracle and transfer donor regression intent

**Files:** retain `src/test/resources/legacy/*`; modify `LegacyCompatibilityTest.java`, `LegacyOperationsTest.java`, `FeatureExtensionCropTest.java`, `domain/chamber/ChamberStateTrackerTest.java`, `concurrency/ApplicationLifecycleTest.java`; create `src/test/java/org/example/svfrms/FinalizedRegressionTest.java`.

**Interfaces:** existing `SvfrmsApplicationContext` constructors/facade methods remain; original fixture SHA/source remains unchanged. New tests call the final service graph, not copied donor applications.

- [x] Run GPT's oracle suite before changes: `mvn -Dtest=GoldenMasterLegacyTest,LegacyCompatibilityTest,LegacyOperationsTest test`. Require complete console/log/report/per-step comparison and the original digest/code-source checks. Never regenerate fixtures to fit new behavior.
- [x] Add named regression cases `legacyDisabledRulesStayDisabled`, `allSixDevicesPreserveClamps`, `unknownChambersReceiveSummaries`, `removedCropKeepsOnlyLegacyGlobalFallback`, `rawNonFiniteValuesRetainOriginalComparisons`, `queuedControlDoesNotDisappearWhenCapacityIsExceeded`. Assert Tomato-only soil; Tomato/Strawberry-only humidity; strict original costs; CH5 state/summary creation; no fixed1024 silent-drop boundary. Use synchronous/direct test input or drain between readings to avoid deadlocking the original oracle's five-slot queue.
- [x] Adapt modernization's seeded/boundary intent to the existing independent oracle, and Astra's whole-state/count assertions to GPT's actual atomicity scope. Do not assert a whole-farm live snapshot that GPT does not promise. For CAS tests assert unrelated-field preservation, not an invalid ordering between new telemetry and old control.
- [x] Run added tests; existing correct behavior should pass. For any discovered failure, record it, add a failing assertion first and apply only a narrow fix. Do not invent failing tests by changing correct behavior or importing entire donor suites with incompatible APIs.
- [x] Verify no feature/API/report section was removed, run the three mandatory commands, then commit the regression coverage and adoption evidence.

## Task 3: Make same-target report persistence whole-file safe

**Files:** modify `src/main/java/org/example/svfrms/infrastructure/storage/FileReportStorageAdapter.java`; create `src/test/java/org/example/svfrms/infrastructure/storage/FileReportStorageAdapterTest.java`; extend existing `service/report/ReportServiceTest.java`.

**Interfaces:** retain public `FileReportStorageAdapter(Path)`, `FileReportStorageAdapter()`, `void writeLines(List<String>) throws IOException`, `Path reportFile()`. Add a package-private constructor `(Path, FileOperations)` and package-private nested `FileOperations` methods `Path createTemp(Path directory)`, `void writeUtf8(Path,List<String>)`, `void replace(Path temporary,Path target,boolean atomic)`, `void deleteIfExists(Path)` throwing IOException. The default implementation wraps NIO; the seam exists only to make failure paths deterministic.

- [x] Add tests `concurrentSavesProduceOneCompleteCandidate` with two separate adapters and different-size A/B payloads, `failedPreparationLeavesPreviousReportUntouched`, `unsupportedAtomicMoveUsesReplacementFallback`, `realMoveFailurePropagatesAndCleansTemporaryFile`, `unicodeAndTruncationMatchLegacy`. Record current missing guarantees as red tests. A latch-controlled file-operations double provides the red proof; repeat the real filesystem race as supplementary evidence, not the only test.
- [x] Write a unique temporary file in the target directory using UTF-8 and close it before replacement; try `ATOMIC_MOVE` with `REPLACE_EXISTING`. Catch only `AtomicMoveNotSupportedException` for fallback replacement. Propagate unrelated exceptions. Clean temp files in finally without hiding the original exception; document that fallback does not promise crash-atomic replacement on every filesystem.
- [x] Ensure same-target writers cannot stream into the destination. Temporary names are unique; publication produces complete candidates even across adapter instances. Do not add state/alert locks around disk I/O or claim cross-process durability.
- [x] Run `mvn -Dtest=FileReportStorageAdapterTest,ReportServiceTest,ApplicationLifecycleTest,LegacyOperationsTest test`; require all failure-path assertions and legacy CLI saved/error text still pass. Then run all three mandatory commands and commit the narrow storage change.

## Task 4: Restore concurrent report/archive output grouping

**Files:** modify `src/main/java/org/example/svfrms/service/report/DailyReportService.java`; extend `src/test/java/org/example/svfrms/service/report/ReportServiceTest.java`; extend `concurrency/ApplicationLifecycleTest.java` if needed for callbacks.

**Interfaces:** preserve `void recordDailyReport()`, `List<AlertEvent> archiveAlerts()`, `void recordReportEntry(String)`, `List<String> reportEntries()`, `List<String> saveArchiveReport() throws IOException`, and protected `DailyReportSnapshot extractMetrics()`.

- [x] Add `archiveCannotSplitDailyReportOutput` and inverse-order tests with a sink latch after the header. Assert archive cannot complete its presentation while daily output is paused, then release the latch before joining; require one entire daily block before or after the archive line. Current code must fail the grouping assertion.
- [x] Add `blockedReportSinkDoesNotBlockSensorStateUpdates`, `reportSinkCanInspectFacadeState`, and `diskSaveDoesNotHoldPresentationLock`. Use bounded waits and release all gates in finally. Test getter reentrancy; do not promise callbacks can synchronously wait for unrelated report work.
- [x] Introduce one dedicated reentrant report-operation lock. Coordinate history mutations, daily/archive output and snapshot extraction under it; capture alert/resource/summary data with existing short state boundaries. Release all state locks before sink calls. `extractMetrics` returns before file persistence begins, so disk save holds neither state nor presentation lock.
- [x] Retain GPT's defined report-before-archive main order, exact original messages, active/archived semantics and save-error contract. Run `mvn -Dtest=ReportServiceTest,LegacyOperationsTest,GoldenMasterLegacyTest,FacadeShutdownTest,ApplicationLifecycleTest test`, then the three mandatory commands; commit.

## Task 5: Consolidate only concrete duplication and log ownership

**Files:** create `src/main/java/org/example/svfrms/command/DeviceCommandFactory.java`, `src/main/java/org/example/svfrms/service/telemetry/SensorLog.java`; modify `infrastructure/context/SvfrmsApplicationContext.java`, `service/telemetry/ThresholdEvaluationObserver.java`; create matching `command/DeviceCommandFactoryTest.java`, `service/telemetry/SensorLogTest.java` under tests.

**Interfaces:** `public static DeviceCommand DeviceCommandFactory.create(ActuatorType type)` returns the same six existing command classes. `SensorLog` owns `public void append(String entry)` and `public List<String> snapshot()`; snapshots preserve insertion order and cannot mutate the log. Context's existing `sensorLogs()` and facade selectors remain unchanged.

- [x] Add a parameterized six-enum command mapping test and compare direct versus queued outcomes/formulas. Add multi-producer unique-log retention, immutable/prior-snapshot stability and exactly400 normal-run log tests; use a gate and joined writers. Existing equivalent behavior may already pass; test only new interfaces red before implementation.
- [x] Move the two identical mappings into the shared factory; retain string lookup/unknown-device compatibility at the facade boundary. Use a private synchronized append/snapshot list for the write-heavy log. Do not remove the logs, truncate retention or alter their timestamp format.
- [x] Run the focused new tests plus `LegacyOperationsTest,GoldenMasterLegacyTest,ActuatorServiceTest,FullSystemIntegrationTest`; run the three mandatory commands and commit. Defer larger context/lifecycle extraction because it is not required to close a reproduced defect.

## Task 6: Demonstrate add/remove without engine edits

**Files:** create `src/main/java/org/example/svfrms/demo/CropExtensionDemo.java`; create `src/test/java/org/example/svfrms/demo/CropExtensionDemoTest.java`; extend `FeatureExtensionCropTest.java`; create `docs/demo-script.md`.

**Interfaces:** `public static void CropExtensionDemo.main(String[] args)` runs the demonstration in a managed context and exits; package-private `static void run(SvfrmsApplicationContext context, Consumer<String> narration)` permits deterministic testing. Use existing `CropRegistry.register(CropProfile)`, `boolean unregister(String)`, `Optional<CropProfile> get(String)`, `processSensor(...)`, `awaitControls(Duration)` and close.

- [x] Add four-name add/remove tests for Kale/Bell Pepper/Bok Choy/Eggplant, an in-flight profile replacement test, and `demoDoesNotChangeDefaultProfilesOrEngineSources`. Assert one-reading profile consistency; the next reading sees removal; no queued work/history is erased; global fallback remains. Require an actual CH5 alert/action summary.
- [x] Implement the demo using a new immutable illustrative profile with explicit characterized values, narrate register/read/drain/remove/read, and close the owned context. No production preset changes or invented agronomic claims. Keep separate human narration from the default legacy sink format.
- [x] Run `mvn -Dtest=FeatureExtensionCropTest,CropExtensionDemoTest,LegacyCompatibilityTest test`. Verify the packaged demo command and default JAR separately; compare no-argument oracle outputs. Run all three mandatory commands and commit.

## Task 7: Measure branch coverage and close schedule-test gaps

**Files:** modify `pom.xml`; modify `ActuatorServiceTest.java`, `TelemetryPublisherTest.java`, `ApplicationLifecycleTest.java`, `FacadeShutdownTest.java`; create `scripts/verify_coverage.py`; update `docs/finalization/verification.md`.

**Interfaces:** `mvn -Pcoverage clean verify` produces `target/site/jacoco/{index.html,jacoco.xml}` and runs the check goal; `python scripts/verify_coverage.py --xml target/site/jacoco/jacoco.xml --base <pinned-GPT-SHA>` enforces changed-decision branch coverage and prints uncovered class/line decisions. No runtime dependency on JaCoCo.

- [x] Add JaCoCo0.8.14 `prepare-agent`, `report`, `check` in a coverage profile. Set bundle line>=0.90, branch>=0.85. Preserve test JVM arguments, fork behavior and subprocess oracle boundaries. Require100% measured coverage on new/modified branch decisions; list uncovered inherited decisions separately without excluding them from bundle totals; report zero-branch classes separately. Do not exclude complex code, shutdown paths or exception handlers to improve numbers. Official release notes support Java25: https://www.jacoco.org/jacoco/trunk/doc/changes.html .
- [x] Fix the close test to wait for a positive admitted backlog before racing shutdown; assert accepted==executed, unique identities and at least one accepted request. Make subscriber mutation genuinely overlap publishing using two gated workers and immutable event input; assert a stable required observer sees every event, with defined optional-subscription semantics.
- [x] Review/adapt Astra scenarios: full-queue close, consumer failure, first-cause preservation, concurrent/second run, self-interrupted source, producer failure, coordinator interruption, reentrant getters and noncooperative callback cleanup. Existing sufficient tests are retained rather than duplicated. Hook tests must invoke the production hook in a child JVM; helper hooks may observe/release but cannot perform production cleanup themselves.
- [x] Run `mvn -Pcoverage clean verify` and the coverage verifier. Fill uncovered meaningful branches with focused tests, including zero-duration timeout and stopped/failed service rejection. Repeat only the bounded concurrency subset20 times as a scheduled stress gate; preserve failures/logs rather than retry-until-green. If a gate cannot be met, keep it FAIL and investigate rather than lower it silently.
- [x] Verify tests from all four branch audits have disposition records and every known defect has final acceptance evidence or an explicit unresolved row. Run the three mandatory commands after final source changes; commit coverage/schedule improvements and exact observed counters. Do not claim concurrency proof from a percentage.

## Task 8: Complete technical report, diagrams and presentation material

**Files:** create/restore editable `docs/technical-report.docx`, optional tracked generation source `docs/technical-report.md`, `docs/report-metadata.json`, `docs/submission-checklist.md`, `docs/diagrams/{component,sequence}.{puml,png}`, `docs/presentation/SVFRMS-FINALIZED.pptx`, `scripts/build_report.py`; update README/demo/provenance/evidence. Deliver root `README.docx` and identical `report.docx`; do not generate PDF deliverables. The implementation plan remains Markdown.

**Interfaces:** `python scripts/build_report.py --metadata docs/report-metadata.json --docx docs/technical-report.docx` creates the editable report; package its final edited bytes as `README.docx` and identical `report.docx`. Do not export PDFs or overwrite manual DOCX edits from stale Markdown. Metadata fields: `group`, `members`, `submission_date`, `confirmed_by_user`. Use confirmed group3. Names are deferred; editable DOCX may leave them blank, with cover completeness recorded as pending. Final cover completion still requires real names; no invented identities.

- [ ] Follow current documents/PDF/presentations skills when authoring. Author and visually verify the editable DOCX first; use PDF only as an internal rendering intermediate if needed for layout checks; it is not a deliverable. Transfer modernization's explanation/demo structure and Astra's generator ideas selectively; rewrite to describe FINALIZED's actual GPT-derived classes and tests. Include source provenance/attribution; do not copy mismatched diagrams, hardcoded groups or unmeasured speedup claims.
- [ ] Use a five-body-page target plus cover: (1) original purpose/components and concrete smells, (2) final component diagram and five SOLID points, (3) actual concurrency defects/solutions and measured tests, (4) justified patterns and add/remove crop flow, (5) verification, limitations and specification traceability. State same-order original report locks and actual liveness hazard accurately. Explain live-snapshot scope and the user-confirmed Section3 scope mismatch visibly, without claiming those missing features were implemented.
- [ ] Validate root/packaged DOCX files render to3-6 body pages excluding exactly one cover and contain real metadata, required headings and a legible architecture diagram. Render every page to images and inspect them; iterate layout. Text extraction/page counts alone are insufficient. Do not certify a placeholder draft as final.
- [ ] Prepare approximately five minutes of slides covering the five required topics and a timed two-minute crop/concurrency demo; include exact commands and a bounded prerecorded/log fallback clearly identified. Keep editable deck/diagram/report sources tracked. Have the team confirm understanding with class notes/Q&A and record actual rehearsal duration, not a generated estimate as fact.
- [ ] Repair all README handoff links and inventory docs, record human-only pending rows honestly, then commit finalized sources/assets only when their corresponding evidence is real. Missing identities may leave report draft work prepared, but keep final cover completion pending. Prepare the editable DOCX deliverables with the names omitted as requested, and disclose that pending fact.

## Task 9: Package and verify the exact submission; final constraint audit

**Files:** create `scripts/package_submission.py`, `scripts/verify_submission.py`; create `submission/SVFRMS-FINALIZED.zip`, `submission/SHA256SUMS.txt`, `docs/finalization/verification.md`; update machine-readable requirement statuses and checklist.

**Interfaces:** `python scripts/package_submission.py --metadata docs/report-metadata.json --output submission/SVFRMS-FINALIZED.zip`; `python scripts/verify_submission.py --zip submission/SVFRMS-FINALIZED.zip --output target/final-verification.json`. The packager uses an allowlist/current build output, not an old committed JAR/ZIP. The verifier returns nonzero for any required unresolved/failed release gate.

- [ ] Verify exact FINALIZED branch and ancestry; run fresh `mvn clean test`, `mvn package`, `java -jar target/run.jar`; capture exit codes/versions. Run coverage gates against these sources. If later changes affect code or DOCX documents, rerun their affected gates and invalidate old hashes.
- [ ] Package `pom.xml`, src/main and src/test/resources/sources, README instructions, curated docs and scripts, root `run.jar` copied from target/run.jar, README.docx/report.docx, the edited technical-report.docx master, deck/demo assets and checksums. Exclude old `svfrms-1.0.0.jar.zip`, source-tree `.class`, src.zip, .git/.review, target/, transient archives and credentials. Reject duplicate/absolute/traversing ZIP names. Keep checksums' self-exclusion explicit.
- [ ] Verify JAR/DOCX/source byte correspondence with the final tree (allow only documented source line-ending normalization); scan application classes for major69 and correct Main-Class. Confirm runtime dependency/module inventory with jdeps; standard Java application does not require test libraries or coverage agent at launch. Ensure the seven stale source-tree legacy class files from old GPT packaging are absent.
- [ ] Extract to a fresh task-owned directory. Run `java -jar run.jar` with no IDE/classpath setup, then the crop demo; verify final active0, generated==archived, chamber alert sum==generated and archived text count matches. Test the legacy handled filesystem-save failure. Use bounded owned subprocesses. Build/test extracted Maven source and compare its application class bytes to packaged JAR classes; hashes from the unextracted checkout alone are insufficient.
- [ ] Run DOCX content/rendered-page/alias checks and visually inspect the final extracted documents. Record C12/C15 as user-directed format deviations rather than PDF compliance; keep any rendering PDFs internal. Verify every C/S row against final hashes and resolve all hard/technical gates and factual identity checks. Retain the three user-confirmed Section3 DOCUMENTED-GAP rows with disclosure, not feature PASS status. Keep actual human upload/rehearsal/registration statuses separate; never claim those actions happened automatically.
- [ ] Request one independent whole-branch review under Superpowers requesting-code-review, focused on constraints, oracle independence, report locking, failure paths and bundle correspondence. Fix findings narrowly and rerun affected/full final gates. Use verification-before-completion before marking success.
- [ ] Commit the final code/docs/artifacts with explicit paths; record commit and hashes (no self-referential requirement to embed the ZIP's containing commit inside its own bytes). Report prepared/verified/pending separately. Do not push/merge/upload without a later user request. If anything remains unresolved, publish a precise pending list and withhold an unconditional “no constraints violated” certification.

## Execution dependency and stop conditions

Order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9. Documentation drafting may accompany implementation, but final diagrams/report and hashes follow final code. Section3-only extensions are excluded by the user-confirmed preservation scope. Member names remain deferred. Prepare editable documents with names omitted; do not claim the cover-name requirement complete. This does not block independent code repairs or preparation of the requested deliverables.

The work stops as complete only after final technical verification, artifact review and required factual scope/identity decisions. Human presentation/submission completion is reported only after the team supplies it. No additional database, dashboard, durable queue, framework, agricultural policy or competing state engine is in this plan.

## Plan self-review record

- Spec coverage: C01-C22 and S31-S35b map every technical/delivery constraint and Section3 discrepancy to a task/evidence gate; the two report base names are retained as DOCX aliases, with the original PDF format requirement disclosed as a user-directed deviation.
- Boundary tests: the five Review Focus conditions have named owning tests; ordinary correct behavior remains protected by the independent oracle.
- Interfaces: existing facade/report signatures are preserved; factory/log/demo/file-operation/script contracts are defined before dependent tasks.
- Proportion/scope: nine reviewable deliverables; test-first applies to missing behavior, not artificial regressions in already-correct donor code; low-value wholesale restructuring is deferred.
- Approval facts: base/native execution are recommendations; design/plan approval and member names remain unconfirmed. Preservation scope and group3 are confirmed; names are deferred by request. DOCX is the requested document deliverable format; Markdown is the requested implementation-plan format. No PDF delivery/export step is authorized by this plan. No new branch/build/feature has been claimed complete by writing this plan.
