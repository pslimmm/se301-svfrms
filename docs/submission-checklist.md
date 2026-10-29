# SVFRMS submission checklist

- [x] Final implementation independently reviewed; findings resolved.
- [x] `mvn clean test`, `mvn package`, and `java -jar target/run.jar` pass on final source.
- [x] Root `run.jar` copied from verified `target/run.jar`; SHA-256 matches bundled artifact.
- [x] Current `src/` and `pom.xml` included; original test fixtures preserved for reproducibility.
- [x] `README.pdf` body is 3–6 pages excluding cover; report rendered and visually inspected.
- [x] Identical `report.pdf` alias included because the specification uses both names.
- [ ] Cover contains actual group number and three team-member names. These facts are currently awaiting user input.
- [x] Report contains original-code evidence, final component diagram, SOLID explanation, concurrency solutions, pattern rationale, and specification discrepancies.
- [ ] Demonstration shows a test-only copy of an existing crop profile being registered and removed, plus unchanged production simulation.
- [ ] Human presentation/demo rehearsal recorded within five/two minutes.
- [x] Submission ZIP extracted into a fresh directory; source builds and both submitted/rebuilt JARs run successfully.
- [x] ZIP excludes `.git`, temporary outputs, target build directory, old archives, stale reports and runtime logs.

Do not mark the assignment submission-ready while identity details or rehearsal evidence are missing. The existing `src.zip` is not automatically the current submission.