# FINALIZED constraint and requirement ledger

Execution status: technical PASS rows are backed by the final build, coverage and exact-package checks. Exact artifact hashes are recorded in submission/verification.json beside the ZIP; full specification compliance remains false for the disclosed format/scope and human facts. Columns distinguish enforceable code/delivery constraints from required human facts and the Section 3 scope discrepancy.

Authority: attached `docs/SE301-Project-Specification.pdf` (PDF pages cited below), user-linked `.review/branches/gpt/docs/se301_project_specification.md`, `AGENTS.md`, and the user's exact target branch. Matching PDF/Markdown technical requirements were reviewed; historical research is supporting evidence, not a replacement specification. PDF §5 calls for README.pdf and §6 names report.pdf. The user instead requests DOCX document deliverables and a Markdown plan; record the report-format requirements as USER-DIRECTED-FORMAT-DEVIATION, deliver README.docx/report.docx, and do not claim PDF compliance.

Status vocabulary: `PLANNED` = implementation/evidence needed; `PASS` = checked on final hashes; `FAIL` = known unmet acceptance; `OPEN-SCOPE` = interpretation unresolved; `DOCUMENTED-GAP` = user-selected omission disclosed explicitly, not an implemented feature; `HUMAN-PENDING` = factual confirmation unavailable; `USER-DIRECTED-FORMAT-DEVIATION` = requested DOCX output differs from the specification's PDF format, disclosed rather than passed. No N/A, exclusion or waiver without written rationale and source. The final release gate rejects FAIL, OPEN-SCOPE and incomplete required human confirmations. Static/source review and tests are bounded evidence, not a proof over every thread schedule.

| ID | Requirement / constraint | Source | Owning task(s) | Required evidence | Status |
| --- | --- | --- | --- | --- | --- |
| C01 | Enhance existing application; preserve behavior; no removed features or wholesale replacement | PDF pp. 6,8, §§4/Restrictions; AGENTS §1 | 1-9 | GPT ancestry; change/adoption ledger; independent original oracle unchanged; complete workflow tests | PASS |
| C02 | Multiple logical source files and component packages, focused responsibilities | PDF p.6 §4.1; AGENTS §2 | 1,5,8,9 | Package/source inventory; SRP review with concrete before/after examples | PASS |
| C03 | All five SOLID review points, with meaningful dependency/interface/substitution reasoning | PDF p.6 §4.2(i-v) | 2,5,6,8,9 | Report five-point mapping; focused interfaces; no fabricated inheritance violation | PASS |
| C04 | Patterns justified by concrete problems; no unnecessary pattern scaffolding | PDF pp.6-7 §§4.3/5; AGENTS §5 | 5,8,9 | Actual Observer/Strategy/Command/Registry rationale; simple alternatives; code matches diagrams | PASS |
| C05 | Identify and rectify races, synchronization and deadlock/liveness hazards | PDF p.6 §4.4; AGENTS §3 | 2-7,9 | Exact-count/state tests; deterministic queue/cancel/failure schedules; no unsupported AB-BA claim | PASS |
| C06 | Guard mutable state, use standard concurrency primitives; no unsafe state exposed | AGENTS §3 | 2-7,9 | Ownership inventory; concurrent data/atomic transactions; copies; callbacks/I/O outside state locks | PASS |
| C07 | Real graceful JVM shutdown hook stops producers and drains/stops workers | AGENTS §3 | 2,7,9 | Child JVM invokes production hook; admitted backlog positive; bounded close/termination evidence | PASS |
| C08 | Add/remove crops with zero monitoring-engine/evaluation-loop changes | PDF p.6 §4.5; AGENTS §4 | 2,6,9 | Kale live demo; four extension-name tests; replacement/removal during evaluation; unchanged engine source | PASS |
| C09 | Java 25 or later, Maven structure; no unauthorized application framework | PDF p.8 Development Environment; AGENTS §6 | 1,7,9 | JDK/Maven versions, release25 bytecode, pom/dependency inventory; no runtime test agent/framework | PASS |
| C10 | Self-contained target/run.jar executable without IDE/classpath setup | PDF p.7 §5; AGENTS §6 | 1,9 | Literal required smoke; manifest; jdeps; extracted ZIP java -jar run.jar | PASS |
| C11 | One ZIP with complete src/, Maven build inputs and run.jar | PDF p.7 §5 | 9 | Allowlisted ZIP inventory; checksums; clean extracted-source build; exact binary/source match | PASS |
| C12 | Specification names README.pdf with3-6 body pages excluding cover; user requests DOCX | PDF p.7 §5; later user format instruction | 8,9 | Deliver README.docx, check3-6 rendered body pages plus cover, disclose PDF-format deviation | USER-DIRECTED-FORMAT-DEVIATION |
| C13 | Cover includes actual group number and all member names; no placeholders | PDF p.7 §5 | 8,9 | Group3 confirmed; member names deferred by user; editable DOCX fields may stay blank, final cover remains pending | HUMAN-PENDING |
| C14 | Report explains legacy purpose, flaws with examples, proposed design, final architecture diagram, extension and pattern justification | PDF p.7 §5 | 8,9 | Section checklist plus legible component diagram; source links and correct lock analysis | PASS |
| C15 | Specification names report.pdf/README.pdf; user requests DOCX instead | PDF p.8 §6, p.7 §5; later user format instruction | 8,9 | Deliver identical report.docx/README.docx at root and in ZIP; no PDF deliverables | USER-DIRECTED-FORMAT-DEVIATION |
| C16 | Five-minute presentation + two-minute demo; five specified topics | PDF pp.7-8 §5; AGENTS §7 | 6,8,9 | Rendered deck; timed script; executable crop and concurrency demo; human rehearsal record | HUMAN-PENDING |
| C17 | Team maximum3, actual eLearn/group registration, one group submission before deadline | PDF pp.8-9 §§6/8 | 8,9 | Team confirmation; assignment deadline 1 Nov 2026 23:59 Asia/Singapore; actual upload is human action | HUMAN-PENDING |
| C18 | Team understands/defends code and AI-assisted work; honor code | PDF p.9 §§9-10 | 8,9 | Class notes/Q&A; actual team review; no fabricated authorship/rehearsal/submission claims | HUMAN-PENDING |
| C19 | Run mvn clean test, mvn package, java -jar target/run.jar | AGENTS Verification Workflow | Every code task,9 | Fresh exit codes/logs after last source change; smoke has bounded owned subprocess | PASS |
| C20 | Exact new branch refactor/svfrms-modernization-FINALIZED | User request | 1,9 | git branch --show-current exact match; ancestry from reviewed GPT base; no overwrite of existing branch | PASS |
| C21 | All four donor versions considered/tested | User request | 1,2,7,9 | Pinned four-branch baseline logs and finding-by-finding adopt/adapt/reject rationale | PASS |
| C22 | Meaningful code branch coverage plus concurrency schedules | User request; engineering gate | 2-7,9 | JaCoCo HTML/XML/check; 90% line/85% branch total; 100% new/modified branch decisions; failure schedules | PASS |

## Section 3 discrepancies under the confirmed scope

Section 3 uses mandatory language but describes functionality missing from the supplied program. Section 4/Restrictions and the previous behavior-preserving remediation scope prohibit quietly redefining existing behavior. These rows **cannot be silently marked PASS or N/A**. The user confirmed preservation of the existing scope and explicit documentation of the mismatch. No Section3-only feature additions are planned. Missing capabilities are recorded as DOCUMENTED-GAP; this is an implementation-scope decision, not evidence those capabilities exist or an instructor waiver.

| ID | PDF requirement | Actual legacy/GPT state | Required disposition | Status |
| --- | --- | --- | --- | --- |
| S31 | p.3 §3.1 crop name and all stated range metadata; example crop ranges | Profiles store ranges; actual enabled legacy checks are asymmetric, unlike examples | Preserve enabled rules/costs; document metadata vs enforcement; verify every profile field | PASS |
| S32 | p.4 §3.2 temperature/humidity/soil/light/nutrient sensing | All five present | Full oracle + five-sensor workflow tests | PASS |
| S33a | p.4 §3.3 pump/fan/grow light/nutrient injector | Present, plus legacy humidity devices | Preserve all six actual controls and formulas | PASS |
| S33b | p.4 §3.3 Heating Unit/Ventilation System/Shade Controller | Absent in original/GPT | Disclose missing devices under the user-confirmed preservation scope; no invented default controls | DOCUMENTED-GAP |
| S34a | p.5 §3.4 temperature/humidity/low-soil/nutrient alerts | Present for original applicable crops | Preserve strict boundaries and intentionally absent rules | PASS |
| S34b | p.5 §3.4 sensor failure + INFO/WARNING/CRITICAL semantics | Severity data exists, full described policy absent; NaN/infinities have legacy semantics | Disclose missing policy; preserve raw-value semantics as the user requested | DOCUMENTED-GAP |
| S35a | p.5 §3.5 daily environmental report | Present | Exact text/order/UTF-8/timestamp normalization/error oracle | PASS |
| S35b | p.5 §3.5 weekly crop/water/energy/nutrient reports | Separate report types absent; resource totals available | Disclose missing report types; do not mislabel current-run counts or nutrient quantities | DOCUMENTED-GAP |

## Release evidence rules

Create machine-readable `docs/finalization/requirements.json` mirroring these IDs with `id`, `source`, `requirement`, `ownerTask`, `status`, `evidence`, `applicabilityRationale` and `humanConfirmed`. Include exact source commit, final JAR/report/ZIP SHA256 and verification time in the generated evidence record. Every required row must resolve truthfully before a release-ready marker is produced. Packaging may create a clearly labeled candidate for inspection; a candidate is not evidence of human submission or all-constraint compliance.

The Section3 scope decision is user-confirmed: preserve existing scope and document the mismatch. Report **preservation gates passed; described Section3 additions intentionally absent and disclosed**, not an unconditional claim that every described capability was implemented. DOCUMENTED-GAP rows may accompany release under the agreed scope, but must never be counted as functional PASS. Human-only registration/submission/presentation obligations cannot be satisfied by a generated checklist. Re-run affected checks after any source/DOCX/ZIP change; never carry earlier hashes forward as current proof.

## Editable master and cover status

Group3 is confirmed. The user requested that member names be skipped for now, document deliverables remain DOCX, and the plan be Markdown. Deliver editable report documents with names omitted; do not invent names. There is no PDF export/delivery step. The original PDF-format requirement and incomplete cover are disclosed deviations/pending facts, not PASS results. Do not block preparation of the requested DOCX files on deferred names, but do not call their cover complete.
