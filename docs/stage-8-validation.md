# Stage 8: final integration and submission package

Completed on 31 October 2026. Stages 6-7 were committed and pushed as `72e406a` before this stage. Stage 8 adds integration evidence and reproducible submission artifacts without changing production Java behavior.

## Original defects and final checks

The original lost counters, unsafe collections and initialization, non-atomic reading/control bookkeeping, delimiter-encoded commands, ignored cancellation, and consumer-failure hangs are documented in the refactoring plan and Stage 1-7 reports. Final review confirms the chosen fixes: one state owner and private lock, immutable snapshots/configuration, typed commands, bounded interruptible queue coordination, supervised task completion, and callbacks/I/O outside state locks. A failure hang is distinguished from circular lock deadlock; no original reverse lock order was demonstrated.

The missing final evidence was repeated integration under simultaneous producers, reports, and archiving, plus a runnable submission independent of an IDE. This stage supplies both. It does not change crop thresholds, random ranges, accounting, device formulas, report formats, startup assignments, or failure policies.

## Added integration evidence

[IntegrationStressTest](../src/test/java/org/example/IntegrationStressTest.java) adds 15 executions to the earlier 280 cases:

| Repetitions | Scenario | Exact assertions |
| ---: | --- | --- |
| 5 | Four producers, one archiver, and one report reader share a six-party barrier across 20 batches. | Each run records 800 distinct alerts/logs, 12000 kWh, and 401 report-history markers. Every inspected snapshot maintains generated = active + archived = summed chamber alerts; unique identifiers detect duplication/loss. Final active count is zero. |
| 10 | The complete default application receives scripted high temperature, low humidity/soil/light/nutrients. | Each run retains 400 logs, 300 alerts and actions, 2060 L, and 480 kWh. Chamber alert/action totals are 100/80/60/60. All accepted commands drain, workers terminate, active alerts reach zero, and the file's archived section exactly equals the state archive. |

Deadlines bound waits and cleanup. No sleep is used as proof of interleaving. Repetitions supplement the lock/ownership argument and existing deterministic failure tests; they are not proof of all possible schedules or a performance benchmark.

## Build and artifact validation

- `mvn clean package`: 295 executions passed, zero failures/errors/skips. Freshly compiled 21 production and 15 test source files with Java 25. The existing Maven/Guice `sun.misc.Unsafe` warning remains non-failing.
- All 280 preceding cases continue to cover the independent six-crop rule table, six devices, legacy helpers, 400-reading default fixture, clock/output/writer boundaries, queue saturation/drain, cancellation/failure, and Kale extension/removal policies.
- `scripts/build_report.py` generates a six-page PDF: a Group 8 cover with blank member-name lines as requested, followed by five technical pages. All pages are rendered and visually checked for legibility, clipping, tables, diagram, and pagination.
- `scripts/package_submission.py` checks current test totals, source/JAR and report-source/PDF freshness, the manifest entry point, and test-only Maven dependencies. It packages only explicit source/docs/script paths and emits a SHA-256 manifest. It refuses a newly added runtime dependency until bundled-JAR packaging is configured.
- `scripts/verify_submission.py` independently checks every ZIP checksum, required entries, byte-identical PDF aliases, Java 25 class versions, and `jdeps` output. It runs the exact JAR bytes from the ZIP through default and Kale entry points in fresh directories with 15-second deadlines. Both must exit zero, have empty stderr, and retain generated/archived/summed-chamber equality and zero active alerts. Evidence is written to `output/submission-validation.json` with the ZIP hash.
- `git diff --check` and Python syntax checks cover the final source/docs/scripts. Test and smoke output stays in temporary directories or `target`; the project-root archive is untouched.

## Deliverables and reproduction

Run from the repository root using Java 25+, Maven, and Python 3:

```powershell
mvn clean package
python -m pip install --target .tools/pdf -r scripts/pdf-requirements.txt
python scripts/build_report.py
python scripts/package_submission.py
python scripts/verify_submission.py
```

The PDF dependencies are authoring tools only; they are not needed to run the Java application. Source files and metadata remain reviewable and versioned; `.tools/`, `target/`, `tmp/`, and generated `output/` are ignored. Regenerate the PDF after editing its Markdown/metadata and visually check it before packaging. Rebuilding packages may change ZIP metadata; this is a reproducible procedure, not a byte-reproducibility guarantee.

| Artifact | Purpose |
| --- | --- |
| `output/svfrms-group-8.zip` | Submission: complete Maven Java source/tests, pom.xml, run.jar, report aliases, documentation, authoring/verification scripts, test summary, and SHA256SUMS.txt. No `.git`, local dependencies, caches, or previous run archives are included. |
| `output/run.jar` | Standalone executable. Run `java -jar run.jar`; the only runtime module is `java.base`, with no third-party production dependencies. |
| `output/pdf/README.pdf` and `output/pdf/report.pdf` | Identical technical report aliases satisfy the two names used in specification sections 5 and 6. |
| `docs/technical-report.md` and `docs/report-metadata.json` | Editable report content and Group 8 cover settings; names are intentionally blank. |
| `output/submission-validation.json` | Independent package checksum and CLI verification results tied to the final ZIP. |

The Kale demonstration uses `java -cp run.jar org.example.CropExtensionDemo` and writes its daily report relative to the working directory. Run demos in separate directories to retain both outputs. Its thresholds are synthetic examples, not agricultural recommendations.

## Architecture and remaining limitations

The final architecture is recorded in Stage 6 and diagrammed in the technical report. FarmApplication composes and supervises; FarmState owns transactions; RuleEvaluator evaluates immutable catalog data; simulation, console, reporting, clock, and writer boundaries remain separate. Command, sample-source Strategy, immutable catalog, and constructor injection solve specific problems without a framework or inheritance hierarchy.

Cancellation remains cooperative; arbitrary callbacks can ignore interruption. The final writer is synchronous and has no timeout. State commit and command admission remain separate on a failed run. Legacy report-save failures print an error, so COMPLETED does not imply durable persistence; truncation is not crash-atomic. Compatibility bookkeeping helpers do not enforce every normal sensor-path invariant. No live configuration editing, retention, crash recovery, or stronger direct-input validation is claimed.

[Feature backlog](feature-backlog.md) defines decisions and proposed acceptance evidence for omitted specification capabilities before implementation. The PDF and runnable crop demonstration are prepared; an actual team presentation and portal submission remain human activities. Team member names remain blank by explicit instruction.

Stage 8 changes remain uncommitted for review. Earlier stage reports retain their historical validation counts and limitations.
