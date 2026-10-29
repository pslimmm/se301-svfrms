# SVFRMS documentation

## Binding requirements and implementation record

- [Project specification](se301_project_specification.md)
- [Approved behavior-preserving plan](superpowers/plans/2026-10-29-svfrms-modernization.md)
- [Remediation evidence](superpowers/plans/2026-10-29-svfrms-remediation-evidence.md)
- [Technical report source](technical-report.md)
- [Presentation/demo script](demo-script.md)
- [Submission checklist](submission-checklist.md)

## Historical research

The [research synthesis](RESEARCH_SYNTHESIS.md) and six reports below informed the
component design. They contain historical proposals and unsupported numerical
predictions; read their remediation correction notices before using them as
implementation guidance. The original source and coursework specification govern
behavior. Research proposals to delete callable features, enable extra crop
checks, standardize legacy charges, or equate immutable copies with atomic
snapshots are superseded by the approved plan.

| Report | Topic |
|---|---|
| [01](research/01_legacy_system_analysis.md) | Legacy analysis and code smells |
| [02](research/02_solid_principles.md) | SOLID and pragmatic abstraction |
| [03](research/03_refactoring_quality.md) | Incremental refactoring and modularity |
| [04](research/04_design_pattern_usage.md) | Pattern selection and justification |
| [05](research/05_concurrency_analysis.md) | Evidence-based concurrency defects |
| [06](research/06_concurrency_improvements.md) | Concurrency remediation proposals |

Report artifacts live at the repository root as `README.pdf` and the identical
`report.pdf` alias. A draft cover means actual group/member identities remain
pending. A prepared demo script is not evidence of a human rehearsal.