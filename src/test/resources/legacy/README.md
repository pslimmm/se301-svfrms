# Frozen original reference

`SVFRMS.java.txt` is the unmodified `src/main/java/org/example/SVFRMS.java` at commit
`a2ed2ed3266e1f676c0528515b52c1a33f2fabc8`. Its Git blob is
`ddcb50768c159274ce274e70732bba70f259e0ac`. The checked-in convention is UTF-8 without
BOM, LF, including the original trailing LF. The provenance test independently
recomputes Git's `SHA-1("blob " + byteLength + NUL + bytes)`; neither checkout
CRLF nor modified source is accepted. The local `.gitattributes` pins resource
checkouts to LF even when `core.autocrlf` is enabled. No production implementation generated
these fixtures.

`LegacyReferenceHarness` invokes the JDK compiler on that source and
`LegacyScript.java.txt`, then launches a new JVM in a temporary working directory.
Its classpath contains only the freshly compiled original and driver. The child
records the original class's code-source URI, and the parent asserts that this
resolves to the isolated classes directory. The modern facade and test classes
are absent from the child classpath. Each run uses fresh static fields, queue,
files and processes. Locale is en_US, timezone UTC, and encoding UTF-8.

## Permitted normalization

Only these substitutions are made, by `LegacyReferenceHarness.normalize`:

1. CRLF becomes LF in generated text.
2. The `Date.toString()` UTC date at the start of a stored/displayed sensor-log
   line, immediately before ` : Processing `, becomes `<TIMESTAMP>`.
3. The date following `Timestamp: ` in the report becomes `<TIMESTAMP>`.
4. The absolute filename following `Archive report saved to: ` becomes
   `<REPORT_PATH>`.

No alert text, values, charges, ANSI escapes, report sections or output ordering
are normalized. Map observations are explicitly enumerated in chamber-name order
by the driver; this only makes the test's state dump deterministic. Original
console output is captured without sorting. `fixedClockKeepsExactModernTimestampRendering`
separately asserts literal modern sensor/report timestamps with a fixed clock and
UTC default timezone, restoring the previous default afterward.

## Script and captured evidence

`script.tsv` is checked-in input, with five TAB-separated columns: operation,
chamber, crop, sensor/control, value. Empty columns are significant. The public
test utility exposes these as `List<Step>` for subsequent modern parity tests.
The script contains 232 operations and 555 sensor-log entries, including:

- All six crop temperature profiles: below lower, equal lower, equal upper, above
  upper. Tomato/Strawberry humidity receives the same four boundaries; Tomato
  soil covers 44, 45 and high values with no high rule.
- Disabled humidity/soil rules for the other profiles, generic light/nutrient
  equality and outside boundaries for every crop, unknown/removed crop names,
  and case-mismatched `tomato`.
- Negative, NaN, positive/negative infinity and negative zero inputs for every
  known sensor, plus unknown and case-mismatched sensor names. No physical-range
  validation is invented: the original stores/logs such inputs, and comparisons
  follow Java floating-point semantics.
- Initial state/summary, all six direct controls repeated ten times to reach and
  repeat their clamps, and unknown/case-mismatched controls. Direct controls add
  actions but no resource charges. Manual irrigation/feeding add 100 water/20
  energy.
- Four actual `ChamberThread.run()` invocations, each executing the original
  twenty iterations. A test-only deterministic Random supplies in-range readings
  for Tomato, Strawberry, Lettuce and Chili. These add exactly 160 water/80 energy
  and 400 retained logs without queue scheduling inside an iteration.
- Explicit alert/log display, report-before-archive ordering, nonempty archival,
  repeated empty archival, and final persisted report.

After every `READ`, the driver dequeues and activates each request using the
original command format before executing the next script operation. This is the
original worker's behavior with a deterministic order; no worker thread is
started, the queue cannot fill, and no scheduling race is asserted as intended
behavior. The driver never changes thresholds, counters or actuator formulas.
Reports run only after commands have drained. An initial empty archive and a
final second empty archive exercise both empty cases.

`expected-console.txt` captures complete stdout, including explicit log/alert
display and ANSI controls. `expected-sensor-logs.txt` captures every retained
log. `expected-report.txt` captures the actual saved UTF-8 file.
`expected-observations.tsv` captures state, summaries, counters, active/archived
alerts and report entries after every operation. Counter columns are water,
energy, active-alert count, generated-alert count, archived count, retained-log
count and queue size. State columns are temperature, humidity, soil, light and
nutrient. Summary columns are alerts, actions, last action and last alert. The
final totals are water 565, energy 205, 52 generated/archived alerts, no active
alerts, 555 logs and an empty queue.

Normal tests never regenerate fixtures. `referenceScriptProducesStableFixtures`
compares two fresh processes with each other and all four frozen outputs.
`goldenMasterDetectsThresholdAndCostDrift` separately compiles temporary source
copies changing Tomato's high-temperature comparison from 28 to 29 and its
energy charge from 10 to 11. Each relevant exact fixture comparison must fail;
the frozen original resource is never changed.

For an intentional, reviewed recapture from the pinned original only:

```text
mvn test-compile
java -cp target/test-classes org.example.svfrms.LegacyReferenceHarness ABSOLUTE_TEMP_DIR src/test/resources/legacy
mvn test "-Dtest=GoldenMasterLegacyTest"
```

Review the original blob hash and fixture diff before accepting any recapture.
