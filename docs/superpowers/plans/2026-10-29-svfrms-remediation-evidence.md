# SVFRMS remediation evidence

Plan: [2026-10-29-svfrms-modernization.md](2026-10-29-svfrms-modernization.md)
Original reference: a2ed2ed3266e1f676c0528515b52c1a33f2fabc8.
Implementation baseline: ab0c1d8.

## Scope rulings
- Preserve implemented functionality; no new production crop presets, devices, alert policies, or report types.
- Keep existing component architecture and current user-selected branch. Leave unrelated .gitignore edits untouched.
- Root report identities and presentation rehearsal require real team input; do not invent them.
- Research corrections supersede historical proposals that change behavior or claim unsupported performance gains.

## Baseline
- mvn test: PASS, 29 tests, zero failures/errors/skips (2026-10-29).

## Execution status
- Task 1: complete; independent reviewer approved spec/quality. Frozen Git blob ddcb50768c159274ce274e70732bba70f259e0ac; 232 operations, 555 logs, full state/counter/output fixtures; threshold/cost mutants detected. Focused 7/7 and full 35/35 pass; packaging and JAR smoke pass.
- Task 2: implementation gates pass; 126 focused and 150 full tests, package and smoke PASS. Independent component review approved. P1 integration follow-up assigned to Task 4: required telemetry errors currently swallowed by production context. Matrix tests cross-check frozen original observations; costs visible before consumer execution; no duplicate command charges.
- Task 3: complete; independent spec/quality review approved. Focused 132/full 162/package/smoke PASS. Modern 232-step script matches complete console/log/report/state/counter fixtures. Main retains completed session for inspection; command returns committed snapshot; summaries atomic.
- Task 4: complete; final four P2 corrections independently re-reviewed APPROVE with no residual material findings. Final mvn clean test 185/185, mvn package 185/185, java -jar target/run.jar exit 0. Queue cap5, interruption/fatal/output/producer failures, concurrent close, actual JVM-hook accepted-work drain, and report phase ordering verified.
- Final review: approved after one correction wave. Root JAR and identical report aliases generated. Extracted submission source also passes 185/185 tests and Maven packaging; both bundled root run.jar and rebuilt target/run.jar execute successfully from the extracted directory with correct report creation.
## Final correction evidence
- final-fix-red.log: four regression assertions fail before correction (suppressed cleanup failure, silent producer interruption, failed session retirement).
- final-fix-green.log: 14 focused lifecycle tests pass after correction.
- final-hook-mutant-red.log: temporarily removing the actual production shutdown hook causes test assertion failure; source restored byte-for-byte before clean build.
- Final whole-branch scoped re-review: all four P2 findings resolved, no residual material findings.
- Final root smoke: 136 generated/active console alerts; 136 archived; 905 L water and 465 kWh energy; saved active count zero.
- Bundled root JAR SHA256: 6462188D726940D662926B23B42AA36FB9C2C7EFF5469C428C34325F30C0B3F5, matched verified target/run.jar before rebuilding extracted source.
- PDF: five body pages plus draft cover, all pages rendered and visually inspected. README.pdf/report.pdf are identical aliases.

## Remaining submission items
- Actual group number and three member names were requested but have not been supplied. Report cover is explicitly marked DRAFT.
- Presentation/demo script prepared; human rehearsal is not claimed.
- Existing user .gitignore modification (docs/) retained. New documentation is physically saved but may be ignored by Git; no files staged or committed automatically.
## Final handoff and decisions
- Kept the user-selected refactor/svfrms-modernization-gpt checkout and preserved the pre-existing .gitignore edit. No automatic commits, merges or pushes. If separate isolation is later needed, move the reviewed changes before other work starts.
- Reference scripts deliberately serialize control draining and normalize only declared time/path/CRLF fields. This proves deterministic parity, while independent concurrency tests prove safety; it does not promise identical random schedules.
- Restored original-supported blank/whitespace identifiers and exact case/spacing rather than keeping added rejection/canonicalization. Callers relying on the first implementation's extra validation must use the documented legacy-compatible behavior.
- Retained post-main inspection/direct/manual/report operations; queued work after shutdown fails explicitly after original alert/charge effects. This prevents the original late-queue hang rather than silently reopening a worker.
- Retire failed facade sessions only after all writers physically terminate; incomplete workers remain owned. A fresh main does not replay an already reported completed-run cleanup error.
- Immutable inspection accessors are an intentional source-level migration from mutable legacy state classes; binary compatibility is not claimed.
- Ignored SDD evidence/log workspace retained because changes have not been committed and these logs are the recovery record.

## Verified artifacts
- run.jar: copied from final verified target/run.jar.
- README.pdf and report.pdf: identical five-body-page report plus draft cover, final render inspected.
- submission/SVFRMS-submission.zip: 86 files; current source, pom.xml, run.jar and both report aliases. No target/.git/archive/old runtime outputs. Extracted fixtures retain .gitattributes and exact original source bytes.
- Clean extraction: mvn clean test 185 PASS; mvn package 185 PASS; java -jar run.jar PASS; java -jar target/run.jar PASS. Both saved reports show generated136/active0/archived136, water905 and energy465 after successful archival.