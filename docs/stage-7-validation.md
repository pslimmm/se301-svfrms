# Stage 7: crop extension workflows and demonstration

Completed on 30 October 2026. Configuration can now be extended or reduced through small immutable operations. The default CLI, original six profiles, four chamber assignments, rule evaluator, device behavior, and report formatting remain unchanged.

## Original defects and fixes

| Problem | Stage 7 change |
| --- | --- |
| Adding/removing a crop in the legacy implementation required editing branches; the Stage 5 catalog still required callers to rebuild configuration collections manually. | `CropCatalog` now exposes registration, removal, assignment, reassignment, and unassignment operations that each return a validated new catalog. |
| Profile removal had no explicit policy for existing chamber assignments. | Removing an assigned crop fails until every affected chamber is reassigned or unassigned. Unknown names fail explicitly. |
| Duplicate or inverted threshold rules could cause duplicate charges or simultaneous opposing actions. | Profiles and global rules reject repeated sensor/direction boundaries; opposing lower/upper bounds must leave a nonzero interval, including across crop/global scopes. |
| There was no executable new-crop demonstration with specified inputs. | `CropExtensionDemo` adds Kale in CH5 using profile data and existing devices, without changing evaluation, state, queue, simulation, or report code. |
| Future configuration changes could accidentally alter rules used by running producers. | All operations retain immutable snapshots. A latch-coordinated integration test derives a replacement catalog while the original application's producer is paused and verifies the original run's exact results. |

## Catalog API and architecture

The existing catalog remains a concrete immutable configuration component. Constructor validation is reused by every operation; no mutable registry, synchronization layer, framework, builder hierarchy, or new pattern is needed. The Stage 6 component architecture still applies.

| Operation | Result and policy |
| --- | --- |
| `withProfile(profile)` | Registers a new case-sensitive name. A duplicate name fails instead of replacing a profile. |
| `withoutProfile(name)` | Removes a known, unassigned profile. Any remaining assignment prevents removal. |
| `withAssignment(chamber, crop)` | Adds a new chamber at the end of startup order. Requires a known crop and a new, nonblank chamber identifier. |
| `reassignChamber(chamber, crop)` | Changes a known chamber to a known crop, retaining its startup position. Reassigning to the same crop is allowed. |
| `withoutAssignment(chamber)` | Removes a known chamber from the new configuration. It does not delete runtime state in an existing application. |

Every method returns a new catalog; callers must retain the result. Neither successful nor failed operations modify the original. New catalogs can be constructed while an application runs, but they apply only when supplied to a new application. Profile-map iteration order is not a startup contract; the ordered assignment list is.

For example, these operations produce configuration for a future run:

```java
CropCatalog original = CropExtensionDemo.catalog();
CropCatalog nextRun = original
    .reassignChamber("CH5", "Spinach")
    .withoutProfile("Kale");
new FarmApplication(nextRun).run();
```

Alternatively, `original.withoutAssignment("CH5").withoutProfile("Kale")` removes the optional chamber and crop. Removing an unassigned default profile such as Cucumber is also supported. Removing a profile does not change the legacy direct-sensor API's unknown-crop fallback: global light/nutrient rules still apply to unknown crop strings.

## Rule validation and compatibility

- At most one `BELOW` and one `ABOVE` boundary per sensor is permitted within a profile, and separately within the global rule list. Different alert text, devices, charges, or threshold values do not make duplicate directions valid within one scope.
- If both directions exist, the `BELOW` threshold is the lower acceptable bound and the `ABOVE` threshold is the upper bound. Lower must be strictly less than upper. Reversed and zero-width intervals fail before startup, regardless of list order.
- Crop/global opposing bounds must also be consistent. Same-direction rules across the two scopes remain additive in profile-then-global order, preserving the existing characterized behavior. There is no deduplication, override, or newly enabled rule.
- Empty profiles, single-sided rules, and omitted sensors remain valid. Boundary equality continues to produce no alert for that boundary. Existing non-finite threshold and negative-usage checks remain in place.
- Configuration validation does not impose new physical measurement limits or add sensor-failure handling. Incoming NaN/unknown sensor behavior is unchanged.

Rejecting malformed custom rules is an intentional tightening of Stage 5 configuration behavior, not a change to any default legacy rule. The model now describes simple acceptable intervals per scope, not escalating multiple thresholds in the same direction. Richer alert policies would require separately specified semantics and tests.

## New-crop inputs

[CropExtensionDemo](../src/main/java/org/example/CropExtensionDemo.java) contains the complete Kale profile and CH5 assignment. These values are **synthetic demonstration inputs**, not agronomic recommendations; the specification names possible crops but does not supply their thresholds.

| Sensor | Trigger | Alert suffix | Control | Charge at detection |
| --- | --- | --- | --- | --- |
| Temperature | `< 16` | Low Temp | None | None |
| Temperature | `> 24` | High Temp | Cooling Fan | 3 kWh |
| Soil | `< 40` | Low Soil Moisture | Water Pump | 10 L |
| Light (existing global rule) | `< 500` | Low Light Level | Grow Light | None |
| Nutrient (existing global rule) | `< 40` | Low Nutrient Level | Nutrient Injector | None |

Humidity has no Kale-specific rule. The ordinary 2 L / 1 kWh charge per complete iteration still applies. All original profiles and global rules remain available in the demo; CH1–CH4 retain their original assignments. The default `SVFRMS` entry point does not opt into the demo.

## Running the demonstration

Build from the repository root with `mvn clean package`. Then run this PowerShell example to keep its report separate from the project-root archive:

```powershell
New-Item -ItemType Directory -Force target/crop-demo | Out-Null
Push-Location target/crop-demo
try {
    java -cp ../svfrms-1.0.0.jar org.example.CropExtensionDemo
} finally {
    Pop-Location
}
```

The report is `target/crop-demo/archive/daily_report.txt`. It includes CH5 alerts/actions when triggered. Random totals and cross-chamber order vary; the demo uses the same five sampling ranges and 20 iterations per chamber as the default application. A repeated run replaces the report in its working directory, preserving the legacy writer policy.

## Tests and validation

[CropExtensionTest](../src/test/java/org/example/CropExtensionTest.java) adds 25 executions to the 255 earlier cases:

- Six workflow tests cover unchanged defaults, registration/assignment isolation, duplicate and unknown targets, removal of unassigned crops, all-assignment removal restrictions, reassignment order, and unassignment down to zero chambers.
- Five sensor cases reject same-scope duplicate directions with identical or differing thresholds/consequences. Three range cases reject inverted/zero-width bounds in either order and across scopes; one case verifies valid empty, one-sided, and ordered bounds.
- Nine below/equal/above cases verify the demo's exact crop alert text, devices, and charges.
- One complete five-chamber run pauses CH5 sampling, creates a replacement configuration that reassigns CH5 and removes Kale, then resumes the original run. Its 500 readings still produce 100 archived alerts and actions (80 in CH5), zero active alerts, 400 L water, and 160 kWh energy. Workers terminate, the queue drains, and report totals match state totals.

The full suite also retains the independent legacy rule table for all six crops, all six device transformations/clamps, unused helper methods, additive crop/global rules, queue shutdown/cancellation, coherent snapshots, and reporting/writer behavior.

| Check | Result |
| --- | --- |
| `mvn test` | 280 tests passed; 0 failures, 0 errors, 0 skipped. All 255 earlier cases remain passing. |
| `mvn clean package` | Freshly compiled 21 production and 14 test source files for Java 25; all 280 tests passed; produced `target/svfrms-1.0.0.jar`. |
| Default packaged CLI | Ran in `target/stage7-smoke-default/` within a 15-second deadline. Exit code 0, empty stderr, normal completion, zero active alerts, no Kale entries. This sample's generated, archived, and summed chamber alert counts all equaled 133. |
| Packaged Kale demonstration | Ran `org.example.CropExtensionDemo` from the same JAR in `target/stage7-smoke-kale/` within a 15-second deadline. Exit code 0, empty stderr, normal completion, zero active alerts, and CH5 Kale entries. Generated, archived, and summed chamber counts all equaled 159. |
| Source review | `git diff --check` passed. New-crop names appear in the demo configuration, not in the evaluator, state owner, simulation, or application coordinator. |

Toolchain: Java 25.0.4.1 / Maven 3.9.11. Maven's bundled Guice still emits its existing non-failing `sun.misc.Unsafe` warning. Test and smoke outputs use temporary paths or `target`; the project-root archive was not changed. Random smoke counts are observations, not required constants.

## Unresolved limitations and next stage

- Configuration remains Java data. There is no configuration file loader, UI, persistence, live application reconfiguration, or automatic replacement of an existing profile. The APIs deliberately require a new catalog/application.
- Demo thresholds are illustrative inputs. Real crop configuration needs separately approved domain values. Descriptive agricultural ranges and specification features missing from the legacy application remain an explicit backlog in the refactoring plan.
- Existing concurrency and persistence limits remain: cancellation is cooperative, state commit and queue admission are separate, final synchronous writer calls have no timeout, and write errors print rather than failing the run status.
- Stage 8 remains the final integration review and submission packaging/report work. It includes the outstanding stress/invariant evidence, `run.jar`, technical report, and submission ZIP; those artifacts are not claimed complete here.

Stage 7 is left uncommitted for review alongside the existing Stage 6 changes. No push is part of this stage.
