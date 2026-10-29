# SDD ledger — plan: docs/superpowers/plans/2026-10-29-svfrms-modernization.md

Pre-flight: Scan interfaces across all 10 tasks.
- Task 1: Baseline characterization harness for legacy `SVFRMS.java`.
- Task 2: Domain records (`SensorReading`, `ChamberSnapshot`, `AlertEvent`) consumed by Tasks 3–10.
- Task 3: Crop strategy & registry consumed by Telemetry (Task 6) and Extensions (Task 10).
- Task 4: State & Resource meters consumed by Actuators (Task 5) and Reporting (Task 8).
- Task 5: Command pattern & bounded queue consumed by Telemetry (Task 6) and Context (Task 9).
- Task 6: Telemetry publisher consumed by Simulation tasks (Task 9).
- Task 7: Alert management service consumed by Telemetry (Task 6) and Reporting (Task 8).
- Task 8: Report generation service consumed by Application bootstrap (Task 9).
- Task 9: Composition root and main bootstrap uniting all components.
- Task 10: Section 4.5 extensions and executable JAR packaging.
Pre-flight check: Interfaces align cleanly with zero unresolved conflicts.

Task 1: complete (commits cc18a21..e8eeeb6, tests: mvn test -Dtest=GoldenMasterLegacyTest → PASS)

Task 2: complete (commits e8eeeb6..b07c2ed, tests: mvn test -Dtest=DomainRecordsTest → PASS)

Task 3: complete (commits b07c2ed..30680b0, tests: mvn test -Dtest=CropRegistryTest → PASS)

Task 4: complete (commits 30680b0..f085b99, tests: mvn test '-Dtest=ChamberStateTrackerTest,ResourceAccountingServiceTest' → PASS)

Task 5: complete (commits f085b99..95db07f, tests: mvn test -Dtest=ActuatorServiceTest → PASS)

Task 6: complete (commits 95db07f..1930723, tests: mvn test -Dtest=TelemetryPublisherTest → PASS)

Task 7: complete (commits 1930723..4f08667, tests: mvn test -Dtest=AlertManagementServiceTest → PASS)

Task 8: complete (commits 4f08667..a7661a4, tests: mvn test -Dtest=ReportServiceTest → PASS)

Task 9: complete (commits a7661a4..d50e982, tests: mvn test → PASS, 27 tests green)

Task 10: complete (commits d50e982..46c8f23, tests: mvn clean package && java -jar run.jar → PASS, 29 tests green)
