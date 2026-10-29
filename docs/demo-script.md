# SVFRMS presentation and demonstration script

Status: prepared; a human rehearsal has not been recorded.

## Five-minute presentation
- 0:00–0:50: Explain the original single class, five sensor streams, six simulated devices, six hard-coded crop names, and daily archive report.
- 0:50–1:50: Show the component diagram. Explain registry data versus evaluation, typed commands, immutable state and focused services. State why no missing Section 3 features were added.
- 1:50–3:10: Show one lost-update example, the consumer-failure hang, and the report/archive phase race. Both original report paths use the same lock order; do not present an invented AB-BA deadlock.
- 3:10–4:15: Explain independent original-source fixtures and boundary/accounting tests. Discuss atomic alert membership and generated count, and joined-writer reporting.
- 4:15–5:00: Show runtime crop registration/removal through test-only copies of a legacy profile. Explain accepted commands and retained history after removal.

## Two-minute demonstration
1. Run `mvn test "-Dtest=FeatureExtensionCropTest,ApplicationLifecycleTest"`. Explain that the additional crop names are test fixtures copied from an existing profile, not newly invented production thresholds.
2. Run `java -jar target/run.jar`. Show successful termination, original control/alert output, pre-archive active count and archived alert count.
3. Open `archive/daily_report.txt`: show post-archive active=0, unchanged generated/resource totals, sorted chamber summaries, and archive history.

Build and download dependencies before presenting. Do not add a product CLI merely for this demonstration. Run the full verification gate before rehearsal. Record actual rehearsal duration separately; this script is not rehearsal evidence.