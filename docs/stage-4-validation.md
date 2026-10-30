# Stage 4: bounded queue and supervised lifecycle

Completed on 30 October 2026. This stage fixes manual-wait cancellation, consumer-failure hangs, late submission, and repeated/concurrent startup. Stage 3's state transactions and snapshots remain unchanged.

## Original defects and fixes

| Defect | Fix |
| --- | --- |
| Queue waits swallowed interruption. | `ArrayBlockingQueue.put` and timed `poll` are interruptible. Application task boundaries restore interrupt status and surface cancellation rather than resuming the wait. |
| Startup joined producers before observing the consumer, so consumer failure could strand full-queue producers indefinitely. | An `ExecutorCompletionService` observes all five workers in completion order. A failed worker aborts admission immediately; the supervisor cancels peer futures and shuts down the executor. |
| Submission could continue after the consumer stopped. | A small admission protocol rejects new requests after graceful close or abort, while tracking already-admitted submitters until they finish or cancel. |
| No explicit normal-drain/failure distinction. | Graceful close drains accepted work. Abort cancels pending submission and stops consumption; failures skip normal completion and final report saving. |
| Reusing or concurrently starting the same instance could create overlapping workers and stale lifecycle state. | Atomic `NEW -> RUNNING` admission permits one run. Terminal states are `COMPLETED`, `FAILED`, and `CANCELLED`; subsequent `run()` calls are rejected. |
| Worker failures were not propagated reliably to the caller. | The original failure is retained before peers are interrupted. `run()` throws with that cause; secondary cancellation does not replace it. Report/archive tasks are supervised too. |

## Architecture and shutdown rules

[ControlQueue](../src/main/java/org/example/ControlQueue.java) contains a capacity-five `ArrayBlockingQueue<ControlRequest>`, an admission monitor, and three states: `OPEN`, `DRAINING`, and `ABORTED`. A submitter registers under the admission monitor, releases it, and performs the blocking insertion. Registration is removed in `finally`. Neither the admission monitor nor the farm-state lock is held during backpressure.

- **Open:** new requests may register; queue insertion determines FIFO order. Duplicate requests remain distinct executions.
- **Draining:** new registrations fail with `RejectedExecutionException`. Already-registered producers may finish their insertion. The consumer exits only when registration and queue contents are both empty.
- **Aborted:** blocked registered producers are interrupted, new submissions are rejected, and consumption stops. The original failure and remaining queued commands remain available for diagnosis. There is no promise to execute all accepted work after failure.

The consumer uses a blocking poll with a 100 ms interval to notice graceful closure when idle. This is a deliberate adjustment to the plan's suggested stop marker: closure does not need queue capacity, a special command variant, or a producer inserting a marker that could block behind a failed consumer. The interval bounds the idle consumer's closure-check delay, subject to thread scheduling; it is not a timeout on ordinary device work.

[SVFRMS](../src/main/java/org/example/SVFRMS.java) owns a five-thread executor: four chamber `Runnable` tasks and one control `Runnable` task. After all four producers complete successfully, it closes admission and awaits the drained consumer. It then schedules archive/report tasks, observes both, and awaits executor termination before printing normal completion and saving the final archive. Both legacy report/archive orderings remain possible.

On worker failure or caller interruption, the application aborts the queue, cancels all submitted futures, calls `shutdownNow`, and waits up to five seconds for termination. Repeated interruptions during this cleanup do not restart the deadline; caller interrupt status is restored. If a worker still runs, the thrown failure includes a suppressed termination diagnostic. Workers are daemon threads so an uncooperative callback cannot keep an otherwise-finished CLI JVM alive indefinitely. Normal runs still explicitly await all workers.

No new framework or runtime dependency is used. The existing typed Command representation remains, with standard JDK producer/consumer and executor facilities. Lifecycle states are an enum, not a hierarchy of State-pattern classes.

## Preserved behavior and API boundaries

- Capacity five, FIFO execution, duplicates, current-state device effects, detection-time accounting, rule boundaries/messages, archive/report text, and the CLI entry point are preserved.
- All 139 prior tests remain. Worker-specific tests now invoke `ControlTask`/`ChamberTask` and explicitly create threads when needed; production no longer subclasses `Thread`.
- `stopControlThread()` now means graceful admission closure, not interruption of an active control. It remains idempotent. Submissions registered before close are allowed to drain; calls after close are rejected.
- `addControlRequest` and `processSensor` keep unchecked signatures. Interrupted submission throws `CancellationException` with interrupt status restored. If cancellation occurs after a sensor transaction, its alert and usage remain recorded; no mitigation is claimed for an unexecuted request.
- `run()` remains interruptible and exposes `runState()` and `workersTerminated()` for diagnosis. A concurrent second invocation is rejected without cancelling the first. Calling `run()` after manually closing admission is rejected without starting workers.
- A package-visible constructor accepts a report path so whole-run tests can use JUnit temporary directories. The default remains UTF-8 `archive/daily_report.txt` with overwrite semantics. No report service or persistence framework was introduced.

## Tests and validation

[ControlQueueTest](../src/test/java/org/example/ControlQueueTest.java) adds six executions covering empty/idempotent closure, an idle consumer, full-queue closure with an admitted blocked producer, abort/cause preservation, interrupted-submission cleanup, and 100 concurrent requests consumed exactly once.

[ApplicationLifecycleTest](../src/test/java/org/example/ApplicationLifecycleTest.java) adds twelve executions covering:

- A successful scripted full run: 400 readings, 160 L, 80 kWh, 20 alerts/actions, empty queue, zero final active alerts, and a saved report.
- Repeated and concurrent startup rejection; late control/sensor submission; startup after manually closing admission.
- Producer failure without overwriting a previous completed report.
- Consumer failure while submitters face backpressure, retaining the consumer's original cause and releasing blocked senders.
- Producer and idle-consumer interruption, restored interrupt flags, and detection-time accounting retained after failed enqueueing.
- Caller interruption and a pre-interrupted caller, with cancellation and worker termination.
- Failure of either the report or archive worker, with no success message or final file.
- A deliberately uncooperative callback: the five-second cleanup deadline produces a diagnostic and `workersTerminated()` remains false until the fixture explicitly releases the callback. The fixture then verifies termination, leaving no worker behind.

Latches, observed waiting states, bounded futures, and joins coordinate these tests. They do not depend on arbitrary sleep delays or assume that a scheduler chooses a particular report/archive ordering.

| Check | Result |
| --- | --- |
| Baseline after queue/supervisor migration | All 139 earlier tests passed. |
| `mvn test` with final lifecycle coverage | 157 tests passed; 0 failures, 0 errors, 0 skipped. |
| `mvn clean package` | Five production and nine test source files freshly compiled for Java 25; all 157 tests passed; produced `target/svfrms-1.0.0.jar`. |
| Packaged CLI smoke | Ran in `target/stage4-smoke/` with a 15-second deadline. Exit code 0, no stderr, normal completion, zero final active alerts. This random sample produced 137 alerts; archive and summed chamber counts both matched 137. |

The toolchain remains Java 25 / Maven 3.9.11. Maven's bundled Guice still emits its non-failing `sun.misc.Unsafe` warning. No project-root archive was touched during validation.

## Remaining limitations and next stage

- Java interruption is cooperative. Arbitrary custom sensor/output callbacks can ignore it. The bounded cleanup diagnostic and daemon policy prevent silent claims of termination and CLI process retention, but cannot forcibly stop callback side effects. No global run-time deadline is imposed; a caller can interrupt a stalled run.
- `run()` supervises its own tasks, not arbitrary threads calling helpers. Queue abort interrupts registered external submitters; callers still own those external threads. Direct compatibility bookkeeping/control helpers remain available separately from the supervised run.
- A sensor transaction and subsequent queue insertion are intentionally separate. Closure or cancellation can reject an insertion after accounting commits; failure does not roll back alerts or already-executed actions. An in-flight control may finish while peers are being cancelled.
- Report-file write errors retain the legacy printed-error behavior. `COMPLETED` means sensor/control/report-worker processing completed; callers must inspect the existing save-error output to know whether persistence succeeded. Concurrent applications targeting the same file are still not coordinated.
- Crop rules are still hardcoded in `FarmState`; clock/report extraction, data-driven profiles, feature gaps, and final submission packaging remain outstanding.

Next stage: extract crop configuration and pure rule evaluation while preserving the established behavior and concurrency boundaries.
