# Deferred specification features

These are proposed acceptance criteria for future work, not implemented features or approved agricultural policies. The original six profiles, enabled rules, detection-time accounting, and default output remain the compatibility baseline. Confirm domain choices before implementation.

| Feature | Decisions needed | Proposed acceptance evidence |
| --- | --- | --- |
| Descriptive crop ranges and extra enabled rules | Separate metadata from enforcement. Obtain approved bounds, units, and actions for currently omitted humidity/soil/light rules. | Test lower/equal/upper boundaries per newly approved rule; prove descriptive metadata alone never activates a control; rerun legacy tests under default configuration. |
| Heating, ventilation, shade controller | Define transformations, clamps, costs, and interaction with existing controls, including legacy energy-only low-temperature alerts. | Exact formula/clamp tests and FIFO execution tests; confirm action and resource counts occur at the specified time. |
| Severity and sensor failures | Define severity mapping, invalid-number policy, physically impossible values, stale readings, timeouts, and alert repetition/recovery behavior. | Fixed-clock timeout/recovery cases; NaN/infinity/bad-value cases; no unintended mitigation on invalid input; compatibility mode retains old behavior. |
| Weekly and per-resource reports | Define aggregation windows/timezone, units, nutrient-consumption tracking, and persistent history. | Boundary-date fixtures, resource conservation checks, deterministic snapshot exports, and restart/history tests. |
| Configuration persistence and editing | Choose schema/versioning, atomic save policy, profile replacement semantics, authorization/UI needs, and whether edits apply only on restart. | Invalid-file and unknown-assignment rejection without partial updates; round-trip tests; old configuration retained on load failure. |
| Live reassignment/removal | Define handling of queued controls, in-flight readings, old alerts, and accounting during a change. | Barrier-driven transition tests with queued and active operations; no mixed-version evaluation or orphaned commands. Until defined, use a new application with a new immutable catalog. |
| Durable runs | Report write failures now fail supervised runs. Decide atomic file replacement, retry/idempotency and crash recovery; an interrupted or failed write may still damage the previous report. | Inject write/rename failures, assert prior report survives where promised, and simulate restarts without duplicate committed actions. |
| Submission cover | Obtain the actual group members' names and enter them in `docs/report-metadata.json` before generating the final PDF. | Inspect the generated cover and verify every member's name. |
| Submission version control | The Stage 8 scripts and report sources are currently untracked in this workspace. Include them in the eventual commit; a clean checkout otherwise lacks the packaging workflow. | Build and package from a clean checkout of the submitted revision. |
| Continuous operation | Bound histories/retention and worker count; decide backpressure, timeout, and monitoring policies. | Bounded-memory soak runs, stalled I/O diagnostics, cancellation deadlines, and capacity/load tests against agreed service targets. |

Stage 8 completes the finite refactoring and packaging scope. This backlog is deliberately separate from that completion claim.
