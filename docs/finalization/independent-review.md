# Independent whole-branch review

Review range: fe7b6d27cc9f674bfec352cae55afea58a440a05..036a583. Reviewer: fresh-context GPT-6 Astra, read-only, one review with no delegated reviews. Verdict before correction: ready with fixes. No Critical findings; one Important and one Minor.

## Important finding and correction

The unsupported-atomic-move fallback requested non-atomic replacement. The reviewer inspected the installed JDK25 WindowsFileCopy implementation: it can delete the old target before a later move failure. The fault-injected test that threw before replacement did not expose that risk. The reviewer's attempted isolated runtime probe was sandbox-blocked; its evidence was source inspection, not a claimed runtime reproduction.

The correction removes REPLACE_EXISTING from the non-atomic move. An absent destination can receive the complete closed file; an existing or concurrently created destination causes a visible exception without requesting deletion of the previous report. Atomic replacement remains the normal path. unsupportedAtomicMoveCreatesNewReportButPreservesExistingReport was observed failing first, then passing. It covers creation, refusal of overwrite, previous content and temporary cleanup. The full280-test suite, package and required executable smoke passed afterward. The report description was updated and all six DOCX pages inspected again.

## Deferred minor

docs/diagrams/sequence.svg and its PNG inaccurately route control effects and report capture to Evaluation. The accompanying sequence.puml uses self-effects and the main report component figure is accurate. This auxiliary diagram correction is deferred; use the PlantUML source and report component figure when explaining ownership.

## Reviewer strengths

The review found narrow incremental changes, an unchanged independent legacy oracle, independent state/presentation/storage ownership, deterministic lifecycle and profile tests, and extracted-source/JAR byte comparison. The changed-decision coverage ruling was accepted because all inherited branches remain in the denominator and residuals are disclosed.

## Declined-to-judge dispositions

1. Section3-only devices/policies/reports: user selected preservation; retained as DOCUMENTED-GAP, never functional PASS.
2. PDF format and member names: explicit DOCX instruction and name deferral govern preparation; format deviation and cover incompleteness remain disclosed.
3. Registration/rehearsal/understanding/submission: human evidence is still pending.
4. Crash durability/arbitrary filesystem guarantees: not promised. Ordinary fallback error preservation was required and fixed above.
5. Whole-farm transactional live snapshots: not promised; final reporting follows worker termination.
6. Callbacks waiting for another report operation or refusing cancellation indefinitely: unsupported cooperative-callback behavior remains documented; getter reentrancy and ordinary shutdown are tested.
7. Agronomic validity: demonstration policy remains explicitly illustrative.
8. Transient ZIP/status updates: final release-side verification is regenerated after content changes; a candidate is not final proof.

The execution ledger records the corresponding rulings and their costs. There was no second reviewer pass; the correction was verified by an observed red/green regression and the full required workflow.
