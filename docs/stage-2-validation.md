# Stage 2: typed control requests

Completed on 30 October 2026. This stage replaces the control queue's string encoding while retaining its existing synchronization and device execution behavior. It follows the [Stage 1 baseline](stage-1-validation.md); no later-stage concurrency fix is included.

## Original defect and fix

The queue stored `chamber + "|" + control`, and the consumer split that string and used its first two parts. A chamber named `CH|Cooling Fan`, for example, could cause an action on `CH` rather than the intended chamber. Other embedded delimiters could turn the second part into an unknown device, silently dropping the action. Unchecked device strings also allowed misspelled queued commands.

- [ControlRequest](../src/main/java/org/example/ControlRequest.java) is an immutable record containing the complete chamber identifier and a `DeviceType`. Neither field is encoded into a delimiter-separated payload.
- [DeviceType](../src/main/java/org/example/DeviceType.java) enumerates the six supported controls and retains their exact legacy display names.
- [SVFRMS](../src/main/java/org/example/SVFRMS.java) now enqueues typed requests from every sensor-rule branch. The consumer reads the fields directly, with no string splitting.

## Architecture and pattern choice

The flow is now:

```text
sensor rule
  -> addControlRequest(chamber, DeviceType)
  -> ArrayList<ControlRequest> under the existing queue monitor
  -> ControlThread removes the first request
  -> activateControl(request.chamberId(), request.deviceType().displayName())
```

This is a small use of the **Command** pattern: command data separates the producer's request from its later execution. The existing consumer is the invoker and the activation helper remains the receiver. A record and enum are sufficient; no command hierarchy, execution interface, registry, or framework is needed.

The string-based `activateControl(String, String)` helper is intentionally retained as a temporary compatibility boundary. Its device formulas, unknown-device behavior, action summaries, and console formatting are unchanged. Extracting a typed control service belongs to a later stage.

## Preserved behavior and migration boundaries

- Capacity remains five pending requests, FIFO by insertion order, with duplicate requests retained.
- The same queue monitor, `while` waits, `notifyAll`, and shutdown flag remain. Control execution still occurs outside the queue monitor.
- The consumer drains pending requests after shutdown is signaled; queued controls act on state at execution time.
- Alert thresholds/messages, usage at detection, all six device effects, ANSI output, reports, and the CLI entry point are unchanged.
- The Stage 1 test expectations changed only where they inspected the old queue representation or called the submission method. All 111 baseline test cases remain.

The submission API now accepts `DeviceType` rather than a device-name string, and the public queue's element type is now `ControlRequest`. All repository callers were migrated. This is a source-level change for any external code that directly called `addControlRequest(String, String)` or mutated `controlQueue`; no string compatibility overload was added because this stage is removing untyped command submission. There is no CLI input or persisted queue format to migrate.

Requests reject null chamber/device fields before entering the queue. This is an explicit invalid-command boundary, not a preservation claim for accidental legacy null-to-string conversion. Empty chamber IDs remain accepted, and identifiers containing `|` now reach the intended chamber unchanged. The unrelated sensor-input validation policy remains deferred.

## Tests and validation

[ControlRequestTest](../src/test/java/org/example/ControlRequestTest.java) adds 14 test executions:

- Six device cases verify request fields, exact display-name mapping, routing through the consumer, expected effects, summaries, console activation text, and unchanged usage.
- Five identifier cases cover leading, trailing, repeated, and device-like delimiter content, plus the existing empty identifier behavior. They verify the original chamber receives both its alert and its mitigation without creating a split-prefix chamber.
- A cross-chamber FIFO case uses noncommuting humidity operations: 95 -> humidifier -> 100 -> dehumidifier -> 90. Reversed execution would yield 95, so the test checks execution order rather than only list order.
- A five-request case verifies pending capacity, deferred execution, duplicate retention, and complete drain after shutdown.
- A null-field case verifies invalid requests cannot enter through the submission method.

Tests drain the consumer synchronously to avoid unrelated shared-state races. They do not claim to validate blocked-producer wakeups or failure cancellation; those remain Stage 4 work.

| Check | Result |
| --- | --- |
| `mvn test` | Passed: 125 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn clean package` | Passed: three production and six test source files compiled for Java 25; all 125 tests passed; produced `target/svfrms-1.0.0.jar`. |
| JAR inspection | Contains both new command types, retains `Main-Class: org.example.SVFRMS`, and excludes test/JUnit classes. |
| Diff and caller review | `git diff --check` passed; repository submission calls use typed devices, and the consumer no longer splits request strings. |

Validation used the same Java 25 / Maven 3.9.11 environment as Stage 1. Maven still emits the non-failing Guice `sun.misc.Unsafe` deprecation warning. No dependency or build-configuration change was needed in this stage.

## Unresolved limitations and next stage

The queue and other global state remain public and mutable. Callers can still bypass submission and insert a null queue element directly. Shared counters, lists, maps, chamber fields, and summaries still race; ignored interruptions, consumer-failure hangs, late submissions, and repeated-run state remain unresolved. Typed requests fix representation and routing, not thread safety.

No random multi-producer stress test or complete CLI smoke run was used to infer reliability. Report/archive formats remain covered by the baseline tests. The specification's missing features remain unchanged.

Next stage: centralize state in an instance-owned `FarmState`, make related updates coherent, and add deterministic concurrent-update and snapshot tests. Queue supervision and replacement remain a separate subsequent stage.
