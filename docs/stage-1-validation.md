# Stage 1: legacy behavior baseline

Completed on 30 October 2026. This stage adds characterization tests and Maven test configuration. `src/main/java/org/example/SVFRMS.java` is unchanged; no concurrency fix or application component extraction has been attempted.

## Changes and architecture

The original implementation had no automated tests. Its static state, direct console output, and fixed relative archive path made isolated verification difficult. The baseline now uses JUnit Jupiter 6.0.3 (test scope only) and Maven Surefire 3.5.6. There are no new production dependencies or frameworks.

- [LegacyTestSupport](../src/test/java/org/example/LegacyTestSupport.java) resets all mutable legacy globals before and after each test, captures console output, and restores `System.out`. Surefire explicitly disables parallel JUnit execution and uses one fork. The 120-second fork timeout bounds a hang in the legacy queue, whose waits ignore interruption.
- [LegacySensorRulesTest](../src/test/java/org/example/LegacySensorRulesTest.java) holds an independent table of expected behavior. Tests check externally observable effects and accounting, not just a repeated implementation of crop branching.
- [LegacyControlsTest](../src/test/java/org/example/LegacyControlsTest.java) verifies transformations, helpers, and successful queue draining synchronously. A scripted `Random` substitute uses the existing package-visible field to verify chamber sampling order and baseline usage without changing application code.
- [LegacyReportsTest](../src/test/java/org/example/LegacyReportsTest.java) checks reporting and archiving. [ArchiveReportProbe](../src/test/java/org/example/ArchiveReportProbe.java) runs in a child JVM with a JUnit temporary working directory, so the fixed `archive/daily_report.txt` path cannot overwrite a project archive. Child processes have bounded waits and forced cleanup on timeout. The probe needs only production and test class directories, not JUnit at runtime.

The production architecture remains the legacy monolith. These test-only isolation techniques are temporary; later stages should replace global resets with fresh application instances and subprocess archive fixtures with an injectable writer/path where appropriate. The proposed component architecture remains in [the refactoring plan](refactoring-plan.md#proposed-component-architecture).

## Coverage

| Suite | Executions | Preserved behavior |
| --- | ---: | --- |
| Sensor rules | 85 | All 19 rule boundaries tested below/equal/above; exact alerts and console prefixes; state replacement; summary updates; usage at detection; requested devices; global light/nutrient rules for all six crops and unknown crops; absent crop rules; repeated alerts; unknown sensor logging. |
| Controls | 19 | All six device transformations, ordinary and clamped values, including unusual clamp effects; unrelated fields preserved; ANSI console messages; action summaries; no extra usage charge; queue order and drain after shutdown; current state used at execution; unknown device behavior; irrigation/feeding helpers; 20 iterations and five-sensor order. |
| Reports | 7 | Active-to-archive movement and append behavior; cumulative counts/summaries; empty archives; both permitted report/archive orders; display/event helpers; report sections and units; lexicographic chamber order; UTF-8 content; directory creation; file truncation; handled filesystem failure. |
| **Total** | **111** | **All passing, none skipped.** |

The rule table deliberately retains low-temperature energy charges without a heating action, absent Strawberry/Lettuce soil rules, and global light/nutrient thresholds. These are preservation decisions, not newly recommended domain behavior.

Date strings are checked for structure/presence rather than exact values. Report/archive ordering is exercised explicitly in both sequential orders; no scheduler outcome is required. Synchronous queue tests enqueue fewer than five requests, so they never depend on a consumer racing a blocked producer. The suite does not launch the random four-producer `main` and assert unreliable totals.

## Validation

Environment: Windows 11, Eclipse Adoptium Java **25.0.4.1**, Maven **3.9.11**. Maven required approved access outside the sandbox to reach the installed JDK under the user application directory.

| Check | Result |
| --- | --- |
| `mvn test` | Passed: 111 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn clean package` | Passed: production and five test source files freshly compiled; all 111 tests passed again; produced `target/svfrms-1.0.0.jar`. |
| JAR contents and manifest | Confirmed `Main-Class: org.example.SVFRMS`, Java 25 build metadata, and no test classes or JUnit classes packaged. |
| Diff review | `git diff --check` passed; `git diff --exit-code -- src/main` confirmed application source is unchanged. |

Maven printed a `sun.misc.Unsafe` deprecation warning from its bundled Guice 5.1.0. It did not fail either build and does not originate in application code.

To reproduce, use Java 25+ with `JAVA_HOME` set, then run `mvn test` or `mvn clean package` from the repository root. Surefire reports are generated in `target/surefire-reports/`.

## Original defects and unresolved limitations

Stage 1 closes the missing-test baseline gap only. It does **not** resolve or validate fixes for the defects documented in the refactoring plan:

- Shared counters, lists, maps, chamber state, and summaries still race under concurrent access. Passing sequential tests is not evidence of thread safety.
- Consumer failure can still strand producers; interrupted waits are still swallowed; late submission and repeated-run state are still unsafe.
- No circular lock-order deadlock was identified in this revision. Reporting and archiving both acquire the same locks in the same order.
- Full-queue backpressure, concurrent snapshot consistency, worker failure/cancellation, and real worker termination will need deterministic coordination tests alongside the Stage 3/4 fixes. This stage introduces no flaky stress test asserting a particular race outcome.
- The public static API, string-based request encoding, invalid/null sensor-input policy, clock injection, and specification gaps remain unresolved. Exact null-exception behavior and NaN handling are not frozen by these tests.
- A single scripted chamber verifies 100 readings and base usage of 40 L/20 kWh. A reliable four-producer, 400-reading assertion is deferred until shared-state fixes exist.
- Building the JAR is verified; a complete random CLI run, dependency-bundled `run.jar`, and final submission artifacts are later-stage work.

Next stage: introduce typed control requests while preserving this baseline. No later stage is included in this change.
