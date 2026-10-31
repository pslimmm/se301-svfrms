# Singapore Vertical Farming Research Management System

Group3 finalized consolidation on `refactor/svfrms-modernization-FINALIZED`. Java25+, Maven, standard Java SE runtime; no application framework or IDE configuration.

## Build and run

```powershell
mvn clean test
mvn package
java -jar target/run.jar
```

The default run preserves four original chamber assignments, strict asymmetric crop rules, six device effects and legacy console/archive formats. It writes `archive/daily_report.txt` relative to the working directory. Console reporting precedes archival; the file follows archival. Manual operations remain available through `org.example.SVFRMS`, with immutable inspection snapshots replacing writable legacy global fields.

## Crop and concurrency demonstration

```powershell
java -cp target/run.jar org.example.svfrms.demo.CropExtensionDemo
mvn "-Dtest=ActuatorServiceTest#closeDrainsAcceptedCommandsExactlyOnce" test
```

Kale uses illustrative Tomato-equivalent policy data, not agronomic guidance. The same registry/evaluator handles registration and removal. Global light/nutrient fallback remains after removal. See [two-minute demo](docs/demo-script.md).

## Verification

```powershell
mvn -Pcoverage clean verify
python scripts/verify_coverage.py --xml target/site/jacoco/jacoco.xml --base fe7b6d27cc9f674bfec352cae55afea58a440a05
python -m unittest discover -s scripts -p test_submission_tools.py
python scripts/package_submission.py
python scripts/verify_submission.py
```

The coverage script compares changed decisions against the Git base and therefore runs in this repository. Extracted Java sources build/test without Git history. Coverage gates:90% lines,85% branches,100% of new/modified branch decisions; inherited residual locations remain listed and counted. Coverage does not prove every concurrent schedule. The frozen-original source/resources and isolated-JVM oracle are in src/test/resources/legacy and the matching tests.

## Editable deliverables and provenance

- [Technical report](README.docx) and identical [report alias](report.docx); editing master: [technical-report.docx](docs/technical-report.docx).
- [Six-slide presentation](docs/presentation/SVFRMS-FINALIZED.pptx), with a five-minute notes script.
- [Implementation plan](docs/superpowers/plans/2026-10-31-svfrms-finalized.md) and [approved design](docs/superpowers/specs/2026-10-31-svfrms-finalized-design.md).
- [Constraints](docs/finalization/constraint-matrix.md), [verification evidence](docs/finalization/verification.md), [provenance](docs/finalization/branch-provenance.md) and [submission checklist](docs/submission-checklist.md).

Document delivery is DOCX per the user's instruction, differing from the specification's PDF filenames. Section3-only additions absent from the original system remain disclosed gaps under the confirmed preservation scope. Member names are deferred; human rehearsal, registration and actual submission are not claimed complete.

The final ZIP is `submission/SVFRMS-FINALIZED.zip`. Its checksums, exact JAR execution and extracted-source rebuild are verified by the script above. Old legacy Java27 archives/source-tree class files are excluded. Authoring scripts require python-docx or bundled Node artifact tooling, but these are not dependencies of the Java build or executable. `build_report.py` refuses to overwrite manual DOCX edits unless `--overwrite` is supplied; package_submission.py copies the latest edited master rather than regenerating it.
