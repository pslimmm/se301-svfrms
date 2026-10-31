# Singapore Vertical Farming Research Management System

SE301 technical report

Group 3

31 October 2026

<!-- PAGE -->

## 1 Legacy analysis and preservation boundary

The finalized system continues the existing GPT refactor. It preserves the supplied sensor, control, manual accounting, display and report workflows while correcting report persistence and presentation races. The source oracle is the original 393-line SVFRMS class at commit a2ed2ed3266e1f676c0528515b52c1a33f2fabc8; it is compiled and run in an isolated JVM by the regression harness.

The original combines environmental policy, shared mutable state, device actions, output, thread coordination and file persistence. Concurrent ArrayList writes can lose entries; HashMap check-then-put can create competing chamber objects; integer increments can lose resource charges. Sensor writes race with device read-modify-write operations. Swallowed interruption and consumer failure can leave producers waiting on a full five-slot queue.

Both original report methods acquire alertLock before reportLock. There is no demonstrated AB-BA inversion between them. Their archive/report order is nondeterministic, and writers do not consistently share the reporting locks. Worker joins establish final visibility but cannot recover updates already lost.

### Observable behavior retained

Temperature checks apply to six original crops. Humidity applies only to Tomato and Strawberry; soil moisture only to Tomato. Every crop, including unknown or removed names, retains light below500 and nutrient below40 checks. Equality does not alert. Tomato/Strawberry temperature costs are10 high and15 low energy; low humidity costs20/25 water; Tomato low soil costs50 water. Each simulated chamber cycle adds2 water and1 energy. Manual irrigation/feeding add100 water/20 energy. Direct device controls do not charge resources.

The six device formulas, clamps, ANSI output, stored sensor logs, sorted chamber summaries and UTF-8 archive format remain. Raw NaN/infinity, case-sensitive names and unknown sensors are characterized rather than silently revalidated. The compatibility facade delegates all operations into one session and exposes immutable inspection snapshots instead of writable global collections.

Section3 describes additional devices, failure policies and report types absent from the original implementation. The user selected preservation of existing scope, with those mismatches disclosed. No new default agricultural rules were introduced.

<!-- PAGE -->

## 2 Final architecture and SOLID

[[component]]

The facade and application context compose the existing services and supervise producer/control lifecycles. Telemetry invokes stored logging and threshold evaluation. Evaluation captures one immutable crop profile, commits alert/usage effects and submits a typed request. The single consumer applies atomic chamber transformations and updates summaries. Reporting uses snapshots after producer/control termination; file publication has its own filesystem boundary.

**SRP:** policies, logging, command execution, alert ownership, report formatting and storage have different reasons to change. SensorLog now owns append-heavy retention, and DeviceCommandFactory owns the six-device construction mapping shared by manual and automatic paths. The context remains broad; a larger lifecycle extraction was deferred to avoid unrelated migration risk.

**OCP:** immutable CropProfile data and CropRegistry support registration, replacement and removal without crop-name branches in the evaluator. A reading uses the same captured profile for thresholds and charges.

**LSP:** no artificial crop inheritance hierarchy was added. Device commands and storage implementations must preserve their execution/error contracts; state mutation and output follow successful CAS commit rather than side effects inside a retried update function.

**ISP and DIP:** TelemetryObserver, CropThresholdStrategy and ReportStoragePort provide focused substitution seams. Constructors accept clock, output and storage collaborators. A concrete composition root is appropriate for wiring; it does not imply every dependency needs an interface.

Source is organized into command, concurrency, domain, service, infrastructure and demo packages under src/main/java. The finalized work adds focused components to the existing system instead of replacing its implementation.

<!-- PAGE -->

## 3 Concurrency and reliability

Concurrent maps atomically initialize chamber trackers. AtomicReference transitions replace complete immutable chamber/summary snapshots, preserving unrelated fields and count/message pairs. Alert generation, active storage, archival and alert snapshots share one monitor. Resource LongAdders avoid lost increments and are exact after writers stop; an assembled live report is not advertised as a whole-farm transaction.

The capacity-five control queue has explicit admission, completion and closing states. Accepted requests are tracked through execution/output, so queue emptiness alone is not mistaken for completion. Close rejects new work, drains accepted requests and observes worker failure. Completion-order producer supervision cancels siblings on failure, preserves the first cause and prevents normal finalization before cleanup. The real facade JVM shutdown hook is tested in a child process; callbacks performing facade inspection cannot be blocked by a facade monitor held across worker joins.

### Two reproduced defects repaired

**Whole report publication:** formerly, two saves could truncate and stream into the same file, producing a hybrid. Each save now writes and closes a unique sibling temporary file before replacing the target. Atomic replacement is preferred. Without atomic move support, publication creates only a new destination; an existing report is preserved with a visible save error. Non-atomic overwrite can delete the old file before a later failure, so it is refused. Cleanup failure does not hide the primary error. Crash durability is not promised.

**Report operation grouping:** formerly, archive text could split a daily report block. A dedicated reentrant report-operation lock now groups report output/history and snapshot extraction. Alert/state locks are released before output callbacks. Disk I/O occurs after extraction and outside this presentation lock. Tests gate callbacks and storage to prove state progress, getter reentrancy and independent file-save progress.

The strengthened close test establishes an admitted backlog before racing close. Subscriber mutation genuinely overlaps publication. These and both report/archive orders repeat20 times in the suite. Cancellation of external callbacks remains cooperative; output callbacks must not wait synchronously for another report operation. No state lock is held across queue waits, foreign output, file I/O or joins.

<!-- PAGE -->

## 4 Patterns and crop extensibility

**Observer** separates telemetry logging from threshold processing. Required consumers are synchronous and ordered; failures reach the coordinator instead of silently allowing a partially processed reading to appear successful. Copy-on-write remains suitable for infrequent observer registration, while the write-heavy retained log uses an owned append/snapshot list.

**Strategy** isolates threshold evaluation from immutable profile definitions. **Command** carries typed device work across the queue and encapsulates the original six effects. **Registry** supports crop changes without evaluator edits. A shared construction helper removes duplicated command mapping; a static factory is not claimed to be GoF Factory Method. The reporting template separates extraction, formatting and persistence, although its single concrete implementation remains a modest abstraction cost. Patterns are justified by these responsibilities rather than their count.

### Runtime extension demonstration

Run java -cp target/run.jar org.example.svfrms.demo.CropExtensionDemo. The demo registers Kale using explicitly illustrative Tomato-equivalent policy data, processes a hot CH5 reading and drains its control. It then removes Kale, processes another temperature reading and confirms crop-specific alerts stop. Light/nutrient fallback remains, producing a final total of3 alerts,3 actions and10 energy. No monitoring engine or evaluation loop is edited, and the default six profiles/four chamber assignments remain unchanged.

Kale, Bell Pepper, Bok Choy and Eggplant are covered by extension tests. Replacement/removal while an evaluation is paused demonstrates one captured profile per reading. Removal affects later lookups; it neither erases history nor cancels accepted controls. The profile values used for demonstration are not validated horticultural advice.

### Consolidation choices

GPT supplies the engine, compatibility oracle and shutdown ownership. Modernization supplies useful golden-scenario and explanation/demo structure. Astra supplies deterministic lifecycle/invariant and packaging-verification ideas. Gemini supplies useful domain/CAS ideas and negative regression cases; its altered default policies and unchecked queue offers were not adopted. The provenance ledger records every major finding and the corresponding final acceptance test.

<!-- PAGE -->

## 5 Verification and delivery

[[verification]]

All four donor branches were freshly archived and tested at pinned commits. Their suites passed49/29/185/300 executions. Astra's required target/run.jar smoke failed because its Maven filename differs; the alternate artifact ran. These observations are preserved rather than rewritten as donor success. The final branch retains the original-source digest, independent compiler/JVM oracle, complete output/log/report observations and intentional boundary omissions.

New regressions exposed hybrid persistence and damage to a previous report, then passed after complete-file publication. Report-order regressions failed before coordination and passed afterward. Filesystem fault tests cover unsupported atomic move, replacement failure and cleanup exception precedence. The six-device factory tests assert actual environmental effects;4000 unique concurrent log entries retain per-writer order and stable immutable snapshots.

The coverage gate is90% lines and85% branches, with100% of new/modified branch decisions covered. The initial interpretation requiring every inherited branch in any edited class was refined: wiring changes do not establish exhaustive coverage of unrelated inherited cleanup schedules. All51 classes remain in the bundle denominator; every residual location is listed in coverage-summary.json. No classes were excluded or thresholds silently lowered. Coverage and stress repetitions are evidence, not proof over all schedules.

### Constraints and remaining human facts

Java25 and Maven produce the self-contained target/run.jar without an application framework. The final ZIP verifier checks hashes, unsafe/duplicate entries, source/JAR correspondence, Java25 bytecode, executable/default/crop-demo behavior and an extracted-source Maven rebuild. Old Java27 legacy ZIPs and stale source-tree class files are excluded. Verification results are in docs/finalization and the packaged checksum manifest.

Document deliverables are DOCX per the user's explicit instruction, differing from the specification's PDF filenames. Section3-only additions remain disclosed gaps under the confirmed preservation scope. Group3 is confirmed; member names were deferred and are omitted. Human registration, rehearsal, understanding and actual submission are not certified by generated artifacts. The report therefore does not claim unconditional satisfaction of every submission requirement.

**References:** SE301 Project Specification, Sections3-10; Week7 Code Smells and SOLID, pp.4-27 and30-55; Week7 Multithreading, join/executor sections; Martin2008 Chapter17, G3/G5/G8/G14/G31 and testing heuristics; Pressman2020 Chapter9, information hiding, functional independence and behavior-preserving refactoring. The local six-part research informed review but was cross-checked against actual source.
