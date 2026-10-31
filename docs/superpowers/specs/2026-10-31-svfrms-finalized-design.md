# SVFRMS FINALIZED consolidation design

Status: implemented and technically verified; disclosed human/format/scope obligations remain. Requested output branch: **`refactor/svfrms-modernization-FINALIZED`**, with that exact spelling and capitalization. Planning date: 31 October 2026, Asia/Singapore.

## Intent and acceptance boundary

Consolidate the strongest compatible work from four existing refactors into one incremental continuation of the GPT implementation. Correct reproduced defects, improve verification, and produce the source/JAR/report/submission and presentation materials required by the project specification. Preserve the supplied application's observable workflows and default behavior. Do not replace it with an independently rewritten application.

The design is grounded in the attached nine-page `docs/SE301-Project-Specification.pdf`, the user-linked `.review/branches/gpt/docs/se301_project_specification.md`, repository `AGENTS.md`, the four-branch audit, and the original source pinned at `a2ed2ed3266e1f676c0528515b52c1a33f2fabc8`. The user-linked and workspace Markdown specs have equal normalized text; their byte hashes differ because of line endings. Treat documents as evidence/requirements, not instructions to upload work or contact others.

## Approaches and proposed selection

1. **GPT base with selective adoption - recommended.** Retain its component layout, frozen-original oracle, compatibility facade, queue lifecycle and tested JVM hook. Repair reporting; transfer donor test scenarios, crop-demo ideas and explanatory material. Lowest migration risk and the shortest path to valid deliverables.
2. **Astra base with compatibility/delivery restoration.** Gains whole-farm transaction snapshots and compact code, but requires repairing JAR naming, JVM shutdown, console/error contracts, packaging, documentation and component packages. This is a larger integration change for the current task.
3. **Modernization base with lifecycle repair.** Gains rich configuration and presentation assets, but requires stronger cancellation/admission ownership, exceptional-input compatibility and narrower snapshot claims. More code and migration work than retaining GPT.

Gemini is a source of selected domain/CAS ideas and negative regression cases, not a fourth viable base at its reviewed commit: it changes enabled crop rules and has queue/compatibility defects. A whole-tree merge of the four competing state/lifecycle implementations is outside scope. The base selection above is the author's recommendation, not an assertion that the user has approved a design they have not read.

## Confirmed preservation scope

Section 3 describes Heating Unit, Ventilation System, Shade Controller, sensor-failure alerts/severities and extra report types that are absent or incomplete in the original code. Section 4 and Restrictions require preserving behavior, and the existing remediation plan explicitly selected no new Section-3-only functionality. Do not silently use this discrepancy to invent rules, remove features, or declare missing features compliant.

**Confirmed by the user:** preserve the existing scope and document the Section 3 mismatch. Preserve all actual legacy operations and implement the explicit crop-extension requirement in Section 4.5. Do not add the missing Section-3-only devices, sensor-failure policy or extra report types. Record the omissions as **DOCUMENTED-GAP**, with the user's scope decision as the authority. They are disclosed gaps, not implemented features or an instructor-approved waiver. This settles implementation scope and does not require an opt-in extension addendum.

Final evidence may certify the agreed preservation scope; it must not claim every described Section 3 capability exists. A future request for those capabilities is a separate scoped extension with its own tests.

The specification names `README.pdf` in Section 5 and `report.pdf` in Section 6. The user explicitly requests DOCX document deliverables instead: package `README.docx` and identical `report.docx`, and disclose the PDF format/filename difference. Do not create PDF deliverables or claim this format requirement is met. The report must have **3-6 body pages excluding a separate cover**. The cover must contain group **3** and all actual team-member names. The user has deferred the names for now; an editable document may leave those fields blank, but the final submission cover remains incomplete until they are supplied. Astra's hardcoded group 8 is not verified personal metadata and must not be copied as fact. Presentation preparation targets five minutes plus a two-minute demo; human rehearsal/delivery are separate completion facts.

## Architecture and responsibility boundaries

Retain `org.example.SVFRMS` as the small facade and keep the existing `org.example.svfrms` component packages. Retain a single authoritative context/service graph. Retain alert-store transactional consistency, immutable chamber/summary CAS updates, detection-time resource charging, and post-join final reporting. Do not advertise an assembled live facility report as a transaction across independent stores.

Three narrowly scoped improvements:

- **Complete-file persistence:** `FileReportStorageAdapter.writeLines(List<String>)` writes UTF-8 into a unique sibling temporary file, closes it, and replaces the target. Prefer atomic replacement; only an unsupported-atomic-move result uses a create-only fallback without replacement; an existing target is preserved and the error remains visible. Propagate real write/move failures; clean temporary files; retain the previous report when preparation fails. Public storage/facade contracts remain unchanged. A package-private file-operation seam supports deterministic failure tests without a framework.
- **Report operation grouping:** a dedicated reentrant report-operation lock coordinates daily output, archive notification/history and report-metadata extraction. It is separate from farm/alert/actuator locks. Snapshot state briefly, release state locks before callbacks, and never hold the report lock during worker joins or disk I/O. Concurrent calls cannot split the daily block with archive text; callbacks performing state inspection remain safe. Arbitrary callbacks that wait for another report operation are not supported and must be documented.
- **Small maintainability improvements:** share the duplicated six-device construction mapping via `DeviceCommandFactory.create(ActuatorType)` and replace the write-heavy sensor-log CopyOnWriteArrayList with an owned append/snapshot component. Do not simultaneously redesign lifecycle or introduce a whole-farm lock.

## Crop demonstration

Add an independently invoked `org.example.svfrms.demo.CropExtensionDemo` using the same context, registry and evaluator. Run it with `java -cp target/run.jar org.example.svfrms.demo.CropExtensionDemo`. No change to default `java -jar` output or four standard assignments. Demonstrate registration, processing, control drain, removal, and the retained global light/nutrient fallback. Use explicit illustrative profile values copied from an existing characterized profile; label them demonstration data, not validated agronomy. The demo registers real runtime profiles, rather than only describing test fixtures. Cover Kale, Bell Pepper, Bok Choy and Eggplant in tests; show Kale live. Never edit engine/evaluator source to add/remove a crop.

## Verification strategy

Two coverage axes:

1. **Git branches:** pin all four source commits, run/record their baselines, and map every audit finding and adopted artifact to its origin, final decision and final test. Known donor failures remain recorded; donor branches are not silently fixed or merged. Adapt scenario intent without importing incompatible duplicate systems.
2. **Java control flow:** use JaCoCo 0.8.14, officially supporting Java 25, under a Maven `coverage` profile. Produce HTML/XML and enforce proposed project gates of 90% line and 85% branch coverage. Require 100% reported branch coverage for new/modified branch decisions (execution ruling recorded in verification.md), with all inherited branches included in bundle coverage, plus explicit failure/schedule tests; no fabricated percentages or blanket exclusions. If an unreachable compiler branch cannot be exercised, resolve the specific gate before completion and retain evidence rather than quietly lowering it. Test counts and coverage do not establish schedule completeness.

Retain independent original-source digest verification and subprocess isolation. Add deterministic same-path concurrent save, partial-write/move failure, report/archive ordering, positive-backlog close, real concurrent subscriber mutation, consumer failure, caller interruption, actual JVM-hook and post-stop rejection tests. Use latches/barriers and bounded waits, not timing guesses. Preserve all sensor boundaries, NaN/infinities, unknown/case-sensitive names, manual operations, six device formulas and strict legacy error/format behavior. Final core validation remains `mvn clean test`, `mvn package`, `java -jar target/run.jar`; coverage and packaged-ZIP validation are additional gates.

## Deliverables and release boundary

Source tree with `pom.xml`, `src/`, tests, relevant resources and tracked editable documentation; freshly built `target/run.jar` copied to submission root `run.jar`; editable `docs/technical-report.docx`, with `README.docx` and identical `report.docx` as the document deliverables; diagrams with editable sources; presentation deck and demo script; provenance/constraint/verification ledgers; one clean final ZIP with checksums. Do not package `.review`, `.git`, old Java-27 legacy ZIPs, source-tree `.class` files, transient archives or coverage agents as runtime dependencies.

Adapt Astra's packaging/checksum verification approach without its hardcoded group/path/test names. Extract the final ZIP into an isolated directory, verify hashes and DOCX aliases, run its JAR and crop demo, then rebuild/test its extracted Maven source. Check application class-file major 69, the manifest and runtime dependencies. An agent can prepare the submission and rehearse the demo; only humans can confirm identities, eLearn/group membership, actual presentation readiness and submission. Upload/push/merge are not part of this planning request.

## Confirmed choices and remaining decisions

- Section 3 scope is confirmed: preservation only, with the mismatch documented; no new Section-3-only behavior.
- Group number is confirmed as 3. Team-member names are deferred at the user's request; leave editable fields blank and keep the final-cover completion status pending.
- Execution is approved; native sequential implementation and a final independent review are being used.

## Sources

The detailed audit is `docs/reviews/2026-10-31-refactor-branch-comparison.md`; execution tasks are in `docs/superpowers/plans/2026-10-31-svfrms-finalized.md`; requirement IDs/statuses are in `docs/finalization/constraint-matrix.md`. JaCoCo version support was verified against its [official change history](https://www.jacoco.org/jacoco/trunk/doc/changes.html), release 0.8.14. These documents must be explicitly tracked during execution because the current ignore rules exclude their directories.

## Editable document preference

The user clarified that document deliverables must remain DOCX and the plan must remain Markdown. Deliver the plan as `docs/superpowers/plans/2026-10-31-svfrms-finalized.md`. During implementation, author `docs/technical-report.docx` and package its edited bytes as `README.docx` and identical `report.docx`. Do not include a PDF export/delivery step; PDF may be used internally for visual QA only. Record the mismatch with the specification's PDF requirement truthfully. No finalized technical-report content is claimed before implementation and verification.
