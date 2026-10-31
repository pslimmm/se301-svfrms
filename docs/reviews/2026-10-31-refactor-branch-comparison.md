# SVFRMS: four-branch refactoring review

Reviewed on 31 October 2026 against freshly fetched `origin` references. This is an analysis, not an implementation change. Application source, branch checkouts and commits were left unchanged. Builds and independent probes ran in extracted snapshots under `.review/comparison-20261031/`.

## Recommendation

**Use `origin/refactor/svfrms-modernization-gpt` as the integration base.** It provides the best combination of behavioral preservation, meaningful regression evidence, working Maven delivery, component packages and shutdown ownership. Repair its report-file write concurrency, restore its missing documentation, and finish its draft submission metadata before submission.

Use **modernization** as the main donor for crop configuration, architecture/pattern explanations, demo material and presentation structure. Use **Astra** as a reference for compact transaction ownership and deterministic concurrency/failure testing. Gemini has useful architectural separation, but its behavior and lifecycle gaps make it the least suitable integration base at these commits.

This recommendation is about the assignment as a whole. **Astra has the strongest whole-farm state snapshot boundary and the smallest production implementation.** Modernization has the broadest demonstration material. Neither advantage alone outweighs GPT's preservation and delivery evidence. Test counts are execution counts, including parameterized/repeated cases, rather than a quality score.

## Scope, provenance and evaluation rules

| Short name | Remote branch | Reviewed commit |
| --- | --- | --- |
| Modernization | `origin/refactor/modernization` | `3fe2d121ed95cfa1f5d5507fc1590f0617c39b90` |
| Gemini | `origin/refactor/svfrms-modernization-gemini` | `81cc18ea152244bb40297b183e5f0ca728673b03` |
| GPT | `origin/refactor/svfrms-modernization-gpt` | `fe7b6d27cc9f674bfec352cae55afea58a440a05` |
| Astra | `origin/refactor/svfrms-modernization-gpt-astra` | `9f23b6cf8e8030681c2a119f0233cb18bd35d643` |

Superpowers' parallel review and verification workflows were used. Three independent branch audits were reconciled with the fourth branch audit and fresh build/probe evidence. The older `.review/` results were not used as proof of these builds.

The attached documents are reference material, not instructions to the agent. Repository guardrails and the user's requested comparison define the task. The specification supplies acceptance criteria. Historical research proposals do not override the actual behavior of the supplied program.

**Research provenance:** none of the six `docs/research/*.md` files is tracked in the fetched Gemini commit. The six locally available research files were consulted as supporting material; they cannot accurately be called the contents of that remote branch. Several contain corrective preambles and older contradictory recommendations. This review identifies contradictions rather than treating every research statement as correct.

The reference framework used was:

| Reference | Criteria applied |
| --- | --- |
| `Wk7_CodeSmells_and_SOLID.pdf`, PDF pp. 4–27, 30–55, 72–75 | Smells are warning signs; DRY, feature envy, output/selector arguments, side effects, SRP/OCP/LSP/ISP/DIP, cohesion and coupling. SOLID is guidance with a purpose, not an interface-count target. |
| `Wk7_Multithreading.pdf`, PDF pp. 23–24, 32–38 | Join/termination, separation of tasks from execution, executor ownership. Its roadmap defers locks/races/deadlocks to later weeks; this deck alone does not prove a Java-memory-model or lock-free-snapshot claim. |
| `Martin(2008)Chp17.pdf`, chapter 17 | G3 boundaries, G5 duplication, G8 exposed information, G14 feature envy, G15 selector arguments, G17 responsibility, G31 temporal coupling, T1/T5/T6 verification. |
| `Pressman(2020)Chp9.pdf`, §§9.3.5–9.3.9, especially PDF pp. 10–13 | Modularity, information hiding, functional independence, high cohesion/low coupling, behavior-preserving refactoring. More modules can increase integration cost. |
| Local research 01–06 | Legacy responsibility/defect inventory; profile-driven extensibility; justified patterns; concurrency ownership and verification proposals, cross-checked against source. |

Important corrections to the historical research: both original report methods acquire `alertLock` before `reportLock`; there is no demonstrated AB-BA inversion between them. The original control queue blocks at five entries, rather than being operationally unbounded. Shared `Random` is thread-safe, though contended and schedule-dependent. Main's joins establish final visibility but cannot repair increments already lost. Independent atomics/concurrent collections/list copies do not automatically yield an atomic multi-store snapshot. No speedup or benchmark figures from the research were treated as measured branch results.

## Fresh verification and inventory

Environment: Windows 11, Oracle Java **25.0.4**, Maven **3.9.11**. Maven logs and Surefire XML were checked. Every branch ran `mvn clean test` and then `mvn package`. JAR subprocesses had bounded execution.

| Branch | Clean tests | Package | Literal `java -jar target/run.jar` | Production Java files / lines | Technical PDF in commit |
| --- | --- | --- | --- | --- | --- |
| Modernization | 49 passed; zero failures/errors/skips | Pass | Pass, exit 0 | 84 / 2,735 | `docs/report/README.pdf`: 6 pages, placeholder cover identities |
| Gemini | 29 passed; zero failures/errors/skips | Pass | Pass, exit 0 | 43 / 2,279 | Absent |
| GPT | 185 passed; zero failures/errors/skips | Pass | Pass, exit 0 | 48 / 2,077 | `README.pdf`: 6 pages, cover + 5 body pages; explicitly draft identities |
| Astra | 300 passed; zero failures/errors/skips | Pass | **Fails: missing target/run.jar** | 21 / 1,240 | Absent; editable sources/scripts exist |

Astra's actual `target/svfrms-1.0.0.jar` runs successfully. Its failure is the required output filename, not an inability to execute its application. Production lines include comments/blanks and are descriptive only. Gemini's count includes the retained original God class; Astra's compactness partly reflects placing everything under `org.example` rather than component packages.

All four branches retain `svfrms-1.0.0.jar.zip` containing the same old seven-class legacy application with **class-file major 71 / Java 27**, not the reviewed modernization. That ZIP is unsuitable as the final Java 25 submission. It may be retained as clearly labeled historical input. Gemini and GPT additionally contain root `run.jar` files; their class bytes match their fresh Java 25 compiled classes. GPT also has a current submission bundle. Do not infer stale code from whole-JAR hash differences caused by archive metadata.

## 1. origin/refactor/modernization

### Strengths

- **Genuine responsibility separation:** crop policy, sensor transport, evaluation, actuation, accounting, reporting, console views and application assembly occupy focused components. Domain objects do not normally format console output. `FacilityAssembler` provides an explicit composition root.
- **The best crop/configuration demo:** properties define which checks exist, asymmetric resource costs and response mappings. `--crops`, `--chambers`, `--list-crops`, `--seed`, `--sequential` and `--all-reports` make extension reviewable. Legacy absent checks remain absent in the default crop data.
- **Credible preservation evidence:** a package-renamed original and characterization harness verify committed golden fixtures; the modern path is independently compared to those fixtures. Seeded and boundary scenarios cover console, log and archive output. The golden tests do not rewrite fixtures during normal test runs.
- **Useful local concurrency repairs:** per-chamber synchronized read/modify/write, atomic resource counters, atomic map initialization, alert list/counter ownership, a capacity-five blocking control queue and command failure isolation. Stress tests assert exact totals and alert/archive invariants.
- **Strong assessment material:** technical PDF, diagrams, editable diagram sources, presentation deck, demo script, assumptions and a class-by-class study guide. It explains why extra devices/rules are not automatically enabled.
- **Patterns usually solve real problems:** Observer separates reactions; Command separates execution timing; Registry replaces crop branches; report writers separate formatting/persistence. Template Method shares a real report skeleton across several implementations.

### Weaknesses and gaps

**M1 — P2, shutdown supervision can report success before workers stop.** `FacilitySimulation` catches coordinator interruption, calls `shutdownNow()` without waiting for producer termination, then returns to `run()`, which always performs end-of-day reporting. A deterministic probe gated an injected sensor source: after interruption the caller finished and saved a zero-alert report; releasing the still-owned worker then produced an active alert after that report. The injected source delayed cooperative termination; the application must not certify completed processing while that work remains alive. Timeout/error branches also log and continue. Use an explicit outcome/close boundary, await termination with a bound, and suppress successful finalization after failed cleanup. **No shutdown hook exists**, separately missing the guardrail.

Evidence: [FacilitySimulation.java:45–78](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/service/FacilitySimulation.java#L45); `modernization-cancellation-probe.log`.

**M2 — P2, control admission has no lifecycle state.** After `stopAndDrain()` has terminated the worker, `request()` still returns normally after placing a command in the queue. The command is never executed; subsequent requests eventually block. Repeated start/stop, submission racing close and consumer death have no admission contract. `stopAndDrain()` itself is an unbounded `put`, despite the coordinator's documented bounded-wait claim. Add one owned open/closing/closed lifecycle and reject requests after admission closes; guarantee accepted work drains or fails visibly. Normal producer-complete FIFO drain does work.

Evidence: [QueuedControlDispatcher.java:41–65](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/control/QueuedControlDispatcher.java#L41); probe: `AFTER STOP request returned normally, executions=0`.

**M3 — P2, invalid-value policy is a behavior change.** Original Tomato temperature `+Infinity` creates a high-temperature alert, charges 10 kWh and requests cooling. This branch creates a sensor-failure alert, charges zero and requests no fan. NaN likewise gets newly defined behavior. The default finite simulator does not expose it, and the assumptions acknowledge the extension, but this is not equivalent for the existing raw sensor API. Make it an explicitly optional policy or preserve the existing default and document a separately accepted extension.

Evidence: [SensorReading.java:18](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/model/SensorReading.java#L18), [SensorFailureRule.java:18](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/alert/SensorFailureRule.java#L18); direct legacy/modern probe in `modernization-probe.log`.

**M4 — P2, snapshot guarantees are overstated.** Alert storage commits before listeners update summaries/resources; `SnapshotCollector` reads these separate stores individually. A gated listener probe observed one generated alert with its associated 10 kWh charge still absent. End-of-day snapshots are reliable after a successful producer/control join; live snapshots are not whole-event transactions. `FacilitySnapshot` also accepts mutable lists/maps without defensive copies, and `ChamberSummaryTracker.snapshot()` returns a mutable TreeMap. Correct the documented guarantee or introduce a shared transaction boundary where one is actually required.

Evidence: [AlertService.java:34](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/alert/AlertService.java#L34), [SnapshotCollector.java:32](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/report/SnapshotCollector.java#L32), [FacilitySnapshot.java:11](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/report/FacilitySnapshot.java#L11).

**M5 — P2, weak configuration boundaries.** `--cycles 4294967296` is accepted as zero because a long is narrowed to int. Fractional costs are silently truncated through `Double.intValue()`, and NaN range bounds pass the `min > max` check. Validate representable counts, integral/nonnegative costs and finite configured bounds, with explicit treatment of omitted unbounded limits.

Evidence: [CommandLineOptions.java:40](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/app/CommandLineOptions.java#L40), [CropProfileLoader.java:70](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/crop/CropProfileLoader.java#L70), [Range.java:13](https://github.com/pslimmm/se301-svfrms/blob/3fe2d121ed95cfa1f5d5507fc1590f0617c39b90/src/main/java/org/example/svfrms/model/Range.java#L13).

**Maintainability/assessment cautions:** 84 classes are not automatically excessive, but several listener layers and a large object graph make one alert harder to trace. Listener ordering carries accounting/control dependencies; optional subscriber failure can interrupt remaining required effects. Rule evaluation and accounting separately look up the crop, so live profile removal/replacement needs a documented per-reading consistency policy. The weekly report counts the current run rather than a persisted week; the nutrient report counts deficiencies/activations rather than nutrient quantity. Those optional outputs should be described accurately. The PDF has placeholder identities and is nested rather than supplied at the requested submission root. Original callable features have been redistributed across services rather than preserved through a compatibility facade; that is an API migration to explain, not proof those features were deleted.

**Use/improve:** retain its configuration, golden scenarios and teaching material. Repair lifecycle contracts and qualify snapshot/behavior claims before adopting the runtime wholesale.

## 2. origin/refactor/svfrms-modernization-gemini

### Strengths

- Strong initial package separation: immutable domain readings/events, telemetry observers, threshold strategy, typed commands, storage port and composition context.
- `ConcurrentHashMap` initialization and whole-state CAS transformations substantially improve sensor/device state safety; retryable update functions do not emit side effects.
- Runtime crop registration, replacement and removal no longer require adding crop classes or temperature branches. No artificial crop inheritance hierarchy.
- Injected strategy/formatter/storage seams support isolated testing. Virtual-thread use is lightweight, although not evidence of better throughput.
- The built JAR is runnable: the legacy class named by the manifest delegates to the new application. Its name alone is not a packaging defect.

### Weaknesses and gaps

**G1 — P1, default rules change existing behavior.** Humidity checks run for every crop, and low-soil checks run for every crop at 50 L per alert. Original humidity applies only to Tomato/Strawberry; soil only to Tomato. Independent comparisons proved five examples where legacy emits no alert but this version does: Lettuce/Chili low humidity or low soil, and Strawberry low soil. These values occur in normal simulation. Preserve enabled rule sets and charges as data.

Evidence: [DefaultCropThresholdStrategy.java:37–53](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/service/crop/DefaultCropThresholdStrategy.java#L37).

**G2 — P1, full queues silently lose mitigations.** `ThresholdEvaluationObserver` ignores `offer()` failure after recording the alert and charge. A probe generated 1,025 hot-Tomato events without a consumer: 1,025 alerts, 1,024 queued fan commands. The default 400-reading run cannot itself fill this queue, but slow-consumer/higher-volume operation can. Introduce interruptible backpressure or caller-visible rejection/retry.

Evidence: [ThresholdEvaluationObserver.java:74–98](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/service/telemetry/ThresholdEvaluationObserver.java#L74).

**G3 — P1, stop can lose its poison pill.** `stop()` also ignores `offer()` failure. A blocked command plus a full capacity-one queue reproduced a worker that drained its work and then waited forever; a second stop was needed to terminate it. The coordinator ignores `awaitTermination`'s result. Close admission and guarantee stop delivery, or use a lifecycle protocol independent of reserving a queue slot.

Evidence: [ActuatorExecutionService.java:46–64](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/service/actuator/ActuatorExecutionService.java#L46).

**G4 — P1, the old public APIs form a disconnected second system.** Only old `main` delegates. `processSensor`, manual control, irrigation/feeding, displays, counters and file reporting still use old global mutable state. They neither inspect nor operate on the new application. More than five old queued controls can block because main no longer starts that legacy consumer. Old `saveArchiveReport` can overwrite the modern report with unrelated state. Forward callable workflows into a single owned modern session, rather than retain two authorities.

Evidence: [SVFRMS.java:10–249](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/SVFRMS.java#L10), [main delegate:350](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/SVFRMS.java#L350).

**G5 — P2, nominally configurable thresholds are ignored.** Light and nutrient comparisons use 500/40 literals instead of the profile's ranges. Kale light 450 with an accepted range 400–900 produced an unwanted alert; nutrient 45 with a minimum 50 produced none. Crop-name resource checks remain in the evaluator. Resolve the contract explicitly: retain global legacy policies, or support per-profile override/enabled policies consistently; do not advertise unused fields as operative configuration.

Evidence: [DefaultCropThresholdStrategy.java:28–65](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/service/crop/DefaultCropThresholdStrategy.java#L28).

**G6 — P2, archive/report consistency is weaker than legacy.** Individual poll/add transfers are not one archive transaction. The fresh executable report observed generated=197, active=158 during archiving, while the final quiescent file had active=0 and archived=197. Original shared report/archive locks allowed all or none after sensor completion, not an intermediate partial transfer count. Output also interleaves. A coherent alert snapshot/transaction or ordered postprocessing would fix this without holding state locks during I/O.

Evidence: [SvfrmsApplication.java:49–66](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/SvfrmsApplication.java#L49), [AlertManagementService.java:58](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/service/alert/AlertManagementService.java#L58); fresh smoke logs.

**G7 — P2, lifecycle supervision and tests miss consequential failures.** No JVM shutdown hook; sensor loops do not check interruption; submitted futures are discarded, hiding task failures. The test named `GoldenMasterLegacyTest` calls the now-delegating `SVFRMS.main`, so it exercises the modernization rather than a frozen original and asserts headings/existence instead of equivalent outcomes. The existing 29 green tests cannot defend behavior preservation.

Evidence: [SvfrmsApplication.java:33–76](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/main/java/org/example/svfrms/SvfrmsApplication.java#L33), [GoldenMasterLegacyTest.java:27](https://github.com/pslimmm/se301-svfrms/blob/81cc18ea152244bb40297b183e5f0ca728673b03/src/test/java/org/example/svfrms/GoldenMasterLegacyTest.java#L27).

**Other verified gaps:** duplicate active-alert counters disagree after archive (metrics=1,025 versus alert store=0); new chambers create state but omit summaries unless manually pre-registered; no required technical PDF; README launch instructions reference the wrong artifact. Separate atomic count/message fields also permit incoherent live summary pairs. Command execution receives an unused metrics dependency; report formatting has a long parameter list despite a report-data object. The single-subclass report template is a weaker pattern justification than the genuine Command/Observer seams.

**Use/improve:** reuse selected immutable models and CAS ideas. A repair requires a real preservation oracle, one authoritative service graph, policy corrections and a reliable queue/lifecycle contract before cosmetic improvements.

## 3. origin/refactor/svfrms-modernization-gpt

### Strengths

- **Best preservation evidence:** frozen original source pinned to commit `a2ed2ed3266e1f676c0528515b52c1a33f2fabc8`, compiled/run in a separate JVM; complete console/log/report and per-operation observations, boundary/exceptional values and intentionally absent rules. It does not mistake a modern delegate for the oracle.
- **Callable workflow migration is complete:** the small `SVFRMS` facade forwards sensor processing, event handling, controls, manual usage, displays, report/archive operations and immutable state inspection to the same session. Unsafe writable fields are deliberately replaced rather than maintained as a second authority.
- **Strong lifecycle engineering:** admission closure, bounded queue backpressure, first-cause failure propagation, interrupted/full-queue producer handling, cleanup ownership and a real tested shutdown hook. Locks are released before joining workers/calling output, avoiding facade/worker deadlock.
- **State safety at a sensible granularity:** immutable chamber and summary snapshots updated atomically; typed commands; shared alert transaction boundary for active/archive/generated state. CAS update functions remain pure.
- **Good extension discipline:** immutable policy data captures the original asymmetric rule availability/costs; one profile is obtained per reading. Adding/removing profiles does not change evaluation loops. Removing a crop intentionally leaves global light/nutrient fallback rules.
- **Working delivery:** literal required command runs; component packages; 6-page technical report; current root JAR class bytes match freshly compiled source; packaged artifacts exist.

### Weaknesses and gaps

**P2 — concurrent saves can corrupt the report file.** `FileReportStorageAdapter.writeLines` truncates and writes the same path without serialization/replacement. Concurrent valid reports of 20,000 A lines and 10,000 B lines reproduced a final file mixing 16,184 A lines and 3,816 B lines. Both operations completed, yet the result was neither complete report. The normal main path writes once and is unaffected. Serialize saves to a target or generate a temporary file and replace under an explicit same-target coordination policy; keep disk I/O outside alert/state locks. Add a regression test asserting the final file is one entire report. This was also possible in the original writer; it is an unresolved reliability issue, rather than a newly introduced default-run regression.

Evidence: [FileReportStorageAdapter.java:25–32](https://github.com/pslimmm/se301-svfrms/blob/fe7b6d27cc9f674bfec352cae55afea58a440a05/src/main/java/org/example/svfrms/infrastructure/storage/FileReportStorageAdapter.java#L25); `gpt-storage-probe.log` and `GptStorageProbe.java`.

**P2 — concurrent public report/archive helpers can split the daily-report block.** A latch-coordinated probe paused reporting after the header; a concurrent archive call printed its message before the remaining report lines. The original two helper methods held their common locks across each operation, excluding that particular interleaving. GPT's default main calls them sequentially and remains unaffected. Coordinate presentation/history operations with a dedicated boundary while keeping state locks short and accounting for callback reentrancy. This is the same narrow output-grouping issue reproduced in Astra; it is not a claim that the original serialized every unrelated console message.

Evidence: [DailyReportService.java:44–60](https://github.com/pslimmm/se301-svfrms/blob/fe7b6d27cc9f674bfec352cae55afea58a440a05/src/main/java/org/example/svfrms/service/report/DailyReportService.java#L44); `GptReportOrderProbe.java` and `gpt-report-order-probe.log`.

**P2 — documentation and submission metadata are incomplete.** README links to an implementation plan, remediation evidence, technical-report source, submission checklist and demo script that are absent from this remote tree. Its PDF is explicitly draft pending identities. Restore the intended editable sources and supporting material; fill the actual group/member cover; regenerate/review the PDFs and packaged bundle after final code decisions. Current compiled/source alignment does not make the coursework cover complete.

Evidence: [README.md:24–28](https://github.com/pslimmm/se301-svfrms/blob/fe7b6d27cc9f674bfec352cae55afea58a440a05/README.md#L24); tracked-tree inventory and PDF extraction.

**Documented limitation, not a newly discovered default-run bug — reports across components are not globally atomic during live writes.** Alert lists/counts are consistent together, and chamber snapshots are individually consistent, but summaries, usage and report history are separate stores. LongAdder totals become exact at quiescence. The technical PDF explicitly says this; retain that qualification. Require a whole-farm transaction only if live mutually consistent reports become an acceptance condition. Astra provides a useful alternative design for that requirement.

**Maintainability costs:** a roughly 383-line context combines composition, lifecycle and compatibility selectors; context and observer duplicate actuator construction; some concurrent containers/adders are additionally protected by the same alert monitor; a single formatter/template path has more indirection than current variability strictly needs. The sensor log uses a write-heavy CopyOnWriteArrayList, incurring repeated growing-array copies; this is minor for 400 readings but unsuitable for claiming long-running scalability. These are targeted simplification opportunities, not reasons for a wholesale rewrite or new frameworks. Favor explicit lifecycle extraction and one command factory if another change makes them necessary.

**Test/delivery caveats:** one close-race test does not first require a positive admitted backlog and can pass vacuously; a test named concurrent subscribe/unsubscribe actually performs a sequential scenario. Improve those schedules rather than relying on their names. The otherwise current submission ZIP also contains seven stale original `.class` files under its source tree; remove those when regenerating it. Maven ignores those source-tree binaries, and the built executable itself is current.

**Scope alignment:** this branch correctly refrains from enabling all devices/reports listed in the broad system description when they are absent from the actual legacy behavior. Do not penalize that as feature removal. Conversely, adding those behaviors from modernization would be a separate opt-in extension, not a behavior-preserving merge.

**Use/improve:** this is the preferred base. Fix persistence concurrency and delivery/documentation first. Bring over explanation/configuration ideas incrementally while keeping its oracle and lifecycle tests as gates.

## 4. origin/refactor/svfrms-modernization-gpt-astra

### Strengths

- **Strongest coherent state model:** one private farm monitor commits sensor/log/alert/summary/usage effects together; archive moves alerts and history together. Snapshots defensively copy immutable nested data. This is a real transaction boundary, not merely a collection of thread-safe containers.
- **Compact and explainable:** 21 production files, no class per crop/device solely to demonstrate patterns, no unnecessary framework or global Singleton. Sensor source, writer, clock and output are useful injection seams.
- **Excellent supervision tests:** completion-order observation catches producer or consumer failure promptly; first failure is preserved; blocked submitters, full queues, cancellation, callbacks and repeated/concurrent invocation are covered with latches/barriers. Accepted FIFO work drains with admission tracking. No farm lock is held during queue waits, callbacks, joins or file writes.
- **Faithful rule/device arithmetic:** all six crops' enabled rules, asymmetric costs, strict boundaries, global fallback and six device clamps match legacy. Catalog extension/removal validates assignments and leaves earlier catalog instances unchanged.
- **Honest limits:** cooperative cancellation, observation-only queue snapshots and detection-based accounting after failed admission are explained/tested rather than advertised as durable exactly-once processing.

### Weaknesses and gaps

**A1 — P1 delivery failure: wrong required JAR name.** `mvn package` creates `target/svfrms-1.0.0.jar`; `java -jar target/run.jar` exits 1. A packaging script can rename a later copy, but that does not satisfy the specified Maven workflow. Set/align Maven finalName and verify the literal command from clean output.

Evidence: [pom.xml:28–61](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/pom.xml#L28); `astra-probes/required-smoke.stderr.log`.

**A2 — P2: JVM shutdown is disconnected from its good cancellation machinery.** No `addShutdownHook` exists. Caller interruption is tested, but Ctrl+C/JVM shutdown does not automatically traverse that caller's cleanup path. Daemon worker threads avoid pinning the JVM; they do not drain accepted work. Add an idempotent owned shutdown operation and child-process hook test.

Evidence: [SVFRMS.java:7](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/src/main/java/org/example/SVFRMS.java#L7), [FarmApplication.java:175–238](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/src/main/java/org/example/FarmApplication.java#L175).

**A3 — P2: CLI completion/error behavior changed.** Success saves before printing “System finished”, reversing legacy order. When a regular file blocks creation of `archive/`, legacy prints the finished line and a save error, exits 0 and has empty stderr; Astra exits 1 with `UncheckedIOException` and omits the finished line. A strict error result may be sensible, but it is a contract change. Its standalone helper preserves errors while its main path intentionally uses a different policy. Preserve the default CLI contract or explicitly separate an approved strict mode.

Evidence: [FarmApplication.java:215–216](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/src/main/java/org/example/FarmApplication.java#L215), [ReportService.java:47](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/src/main/java/org/example/ReportService.java#L47); real filesystem failure probes against both binaries.

**A4 — P2: report output loses the original block boundary.** Reporting and archiving execute concurrently, and output locks each line independently. A deterministic probe produced the daily header, then archive text, then the remaining daily totals. Original report/archive methods hold their common locks across their print blocks, preventing that split. Astra's state snapshot remains coherent; this is presentation ordering, not corrupted counters. Serialize whole output blocks outside the farm monitor or adopt a fixed postprocessing order.

Evidence: [ReportService.java:28–35](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/src/main/java/org/example/ReportService.java#L28), [ConsoleOutput.java:15](https://github.com/pslimmm/se301-svfrms/blob/9f23b6cf8e8030681c2a119f0233cb18bd35d643/src/main/java/org/example/ConsoleOutput.java#L15); `astra-probes/report-interleave.log`.

**A5 — P2: unfinished deliverables and component organization.** No current final PDF/submission ZIP/root run.jar is tracked; metadata has no actual team members. The editable report/generation scripts are useful but do not establish page count or rendered quality. All source remains in `org.example`, missing the requested component package organization even though it satisfies multi-file decomposition.

**Design tradeoffs:** catalog changes configure a new application; they are not live registration/removal of an already-running instance. That meets minimal architecture-change extension, but should not be advertised as a live registry. Assigned crops must first be unassigned/reassigned before removal. `FarmState` still handles several responsibilities under one ownership boundary and dispatches devices with strings after converting typed requests back to labels. Prefer typed internal activation with a compatibility adapter; do not split the transaction merely to make classes smaller. A single monitor can limit throughput and full-history snapshots have copy costs, but the finite assignment workload supplies no evidence that a lock-free rewrite is warranted.

**Use/improve:** strongest alternative if the team prioritizes compact code and atomic live state snapshots. Fix the five concrete delivery/behavior gaps, then organize packages. Borrow its lifecycle tests into GPT rather than merging the entire competing state architecture.

## Cross-branch alignment

| Dimension | Modernization | Gemini | GPT | Astra |
| --- | --- | --- | --- | --- |
| Finite default crop rules | Strong; exceptional readings changed | Material regressions | Strongest independent oracle | Strong policy/boundary coverage |
| Manual/display workflows | Redistributed service APIs | Disconnected original APIs | Delegating shared-session facade | Instance API migration |
| Crop extension | Data files + registry + CLI | Registry, but hardcoded/ignored policy | Immutable profiles + registry | Validated immutable startup catalog |
| Whole-farm live snapshot | Not atomic; claims too strong | Not atomic; partial archive observable | Explicitly limited; alert transaction only | Atomic farm transaction |
| Queue lifecycle | Good happy path; no closed admission | Dropped work/stop on full queue | Strong tested lifecycle | Strong tested lifecycle |
| JVM hook | Missing | Missing | Present and exercised | Missing |
| Pattern restraint | Useful patterns, larger object graph | Useful seams + residual God class/template overhead | Mostly purposeful, some duplications/indirection | Compact; avoids forced patterns |
| Submission/oral material | Best breadth, cover unfinished | Insufficient | Report/bundle present, source links/cover unfinished | Good editable preparation, final assets absent |

LSP is not established by replacing `extends Thread` alone, and lack of inheritance is not an LSP violation. ISP/DIP do not require interfaces for every class. Reporting over immutable data is not automatically feature envy. Applying these distinctions avoids repeating weak pattern/SOLID claims in the oral defense.

## Proposed improvement sequence

1. **Keep GPT as the base and retain its original-system oracle.** Fix same-target report saves and add the whole-file concurrency regression. Verify report/archive console block behavior for concurrently called public helpers as well as the normal CLI.
2. **Restore editable documentation and finish actual team metadata.** Use modernization's diagrams/demo/study structure as a template, rewrite diagrams to match GPT's real lifecycle, and describe live-snapshot limits accurately. Do not claim unmeasured performance improvements or a demonstrated legacy AB-BA deadlock.
3. **Borrow targeted test ideas from Astra.** Prefer deterministic close/admission/failure schedules and first-cause assertions; avoid copying a second lifecycle implementation or a second authoritative farm state.
4. **Only then consider optional external crop files/CLI.** Reuse modernization's policy representation, enforce boundary validation and prove add/remove without editing evaluation. Existing runtime registry APIs already meet the architectural extension requirement; a new CLI is useful demonstration scope, not a prerequisite to fixing correctness.
5. **Rebuild the exact submission.** Run all three required commands, test the JAR extracted from the final ZIP, check Java 25 class compatibility, include the completed 3–6-page technical report plus cover and diagram, and rehearse the crop extension/concurrency explanation.

No branch should be described as fully submission-ready at the reviewed commit. GPT requires the least risky path to that state. Combining all four codebases would increase duplicate policy, state and lifecycle ownership; selective, test-backed adoption is the safer improvement strategy.

## Evidence index and limits

Fresh raw evidence is retained under `.review/comparison-20261031/` in the workspace: `{branch}-clean-test.log`, `{branch}-package.log`, smoke logs, Gemini's audit probe, GPT's storage probe, modernization's sensor/queue/snapshot and cancellation probes, and Astra's CLI/interleaving probes. The per-branch `gemini-review.md`, `gpt-review.md`, and `astra-review.md` contain additional code locations and reproduction details. Modernization findings are documented directly above.

Random smoke totals are observations of one run, not expected cross-branch constants; use equal scripted input for comparisons. Stress tests do not prove every schedule. Bounded cancellation probes distinguish cooperative from noncooperative external callbacks. This review inspected PDF text/page counts as supporting evidence; it does not certify the visual quality or correctness of unfinished submission PDFs/presentation slides. No application fixes, commits or merges were performed.
