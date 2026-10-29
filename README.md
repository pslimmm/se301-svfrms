# Singapore Vertical Farming Research Management System

SE301 legacy modernization, targeting Java 25 or later and Maven. The application
simulates the supplied four chambers, five sensor types and six existing device
actions; it does not add devices, crop presets or alert policies absent from the
original implementation.

## Build and run

```powershell
mvn clean test
mvn package
java -jar target/run.jar
```

The executable requires no extra classpath or IDE configuration. It creates
`archive/daily_report.txt` relative to the working directory. The console daily
report precedes alert archival; the saved archive report follows it, so their
active-alert counts intentionally refer to different phases.

## Verification and documentation

- [Implementation plan](docs/superpowers/plans/2026-10-29-svfrms-modernization.md)
- [Remediation evidence](docs/superpowers/plans/2026-10-29-svfrms-remediation-evidence.md)
- [Technical report source](docs/technical-report.md)
- [Submission checklist](docs/submission-checklist.md)
- [Presentation/demo script](docs/demo-script.md)

The independent test reference compiles the frozen original from commit
`a2ed2ed3266e1f676c0528515b52c1a33f2fabc8` in a separate JVM. The source,
script, complete fixtures and normalization rules are under
`src/test/resources/legacy/`. Tests require a JDK, not a JRE alone.

The facade preserves callable operational workflows. State/summary inspection
returns immutable snapshots; callers must not rely on the original writable
global collections or mutable inner state objects. This is a documented
source-level migration, not a claim of binary compatibility.

`README.pdf` and the identical `report.pdf` alias are report artifacts. A cover
marked as a draft is awaiting actual team identities; consult the submission
checklist before treating the bundle as ready for coursework submission.
