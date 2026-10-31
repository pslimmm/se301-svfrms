# Consolidation provenance and decisions

The FINALIZED branch continues GPT commit fe7b6d27cc9f674bfec352cae55afea58a440a05. The frozen original oracle remains a2ed2ed3266e1f676c0528515b52c1a33f2fabc8. All donor refs were freshly fetched and match the approved pins. No donor implementation was merged wholesale.

| Source | Decision | Final acceptance evidence |
| --- | --- | --- |
| GPT engine, facade, policies, CAS state, shutdown hook | Retain one authoritative graph and the independent original oracle | GoldenMasterLegacyTest, LegacyOperationsTest, LegacyCompatibilityTest, FacadeShutdownTest |
| Modernization M1 interruption/finalization | Reject its weaker cleanup; retain GPT failure supervision | ApplicationLifecycleTest interrupted/failed/externally closed runs |
| Modernization M2 post-stop admission | Retain GPT closed-admission contract | ActuatorServiceTest full-queue/closed-worker tests |
| Modernization M3 invalid readings | Reject new default validation policy | LegacyCompatibilityTest nonfinite matrix |
| Modernization M4 live snapshots | Document exact post-join scope; no global atomic claim | Technical report and report invariant tests |
| Modernization M5 configuration overflow/NaN | Do not import the optional CLI/config loader | Existing fixed simulation inputs; demo uses validated immutable profiles |
| Modernization report/demo/diagram structure | Adapt explanations to actual FINALIZED classes and crop APIs | DOCX render, demo and diagram inspection |
| Gemini G1 newly enabled rules | Reject altered crop policy | FinalizedRegressionTest disabled-rule cases |
| Gemini G2 dropped mitigations | Retain bounded GPT backpressure, test higher volume | FinalizedRegressionTest capacity stress |
| Gemini G3 lost stop sentinel | Reject offer-only close | ActuatorServiceTest positive-backlog close, FacadeShutdownTest |
| Gemini G4 disconnected legacy state | Retain shared-session facade | LegacyOperationsTest and facade tests |
| Gemini G5 ignored thresholds/name costs | Retain immutable enabled policies and per-reading profile | FeatureExtensionCropTest replacement and boundary tests |
| Gemini G6 partial archive/report | Retain alert transaction; repair output grouping | ReportServiceTest coherent archive and grouping tests |
| Gemini G7 unmanaged lifecycle | Retain GPT supervision and real JVM hook | ApplicationLifecycleTest, FacadeShutdownTest |
| Gemini G8 false golden master | Reject modern delegate as oracle | Frozen original digest, isolated compiler/JVM and mutation-rejection tests |
| Gemini G9 missing report/wrong launch documentation | Generate current DOCX and exact JAR instructions | Packaged artifact verifier |
| Gemini G10 duplicate active counts | Retain single alert-store authority | AlertManagementServiceTest |
| Gemini G11 missing new-chamber summaries | Retain computeIfAbsent ownership | FinalizedRegressionTest CH5 summary |
| GPT report file race | Repair same-target publication | FileReportStorageAdapterTest |
| GPT report/archive output race | Repair helper operation grouping | ReportServiceTest |
| GPT dead documentation links/draft names/stale ZIP classes | Restore curated sources, defer names explicitly, clean final bundle | README link inventory and packaged artifact verifier |
| GPT context size, duplicate command factories, write-heavy COW logs | Remove concrete duplication/log copying; defer broad lifecycle extraction | DeviceCommandFactoryTest, SensorLogTest and oracle |
| Astra A1 wrong JAR filename | Retain GPT target/run.jar configuration | Literal smoke and extracted ZIP smoke |
| Astra A2 missing JVM hook | Retain GPT actual hook | FacadeShutdownTest child process |
| Astra A3 CLI save/order change | Reject behavior change | ApplicationLifecycleTest legacy save-error contract |
| Astra A4 report output race | Adopt regression scenario and fix in existing report service | ReportServiceTest |
| Astra A5 unfinished assets/packages | Retain component packages; adapt packaging verification ideas | DOCX/assets, clean rebuilt ZIP |
| Astra transaction model and deterministic tests | Adapt schedule/invariant intent; do not introduce a competing farm-state model | CAS/alert/queue tests with scoped atomicity claims |
| All four legacy Java27 ZIPs | Historical inputs only; exclude from new package | ZIP allowlist and bytecode69 verifier |

Section3-only devices, reports and validation policies are excluded by the user-confirmed preservation scope and disclosed as DOCUMENTED-GAP. Report delivery is DOCX per the user, explicitly differing from the PDF filenames in the assignment. Group3 is confirmed; names and human rehearsal/submission remain deferred. Baseline results are recorded separately; random smoke totals are observations, not cross-branch constants.
