# Singapore Vertical Farming Research Management System (SVFRMS)
## AI Agent Engineering Guidelines & Project Guardrails

### Restrictions
Teams are expected to enhance the existing application while preserving its current behaviour. Removing existing features, replacing the system with a completely new implementation, or disregarding identified concurrency, design, and maintainability issues is not permitted.

---

### Assignment Constraints & Guardrail Expansions

#### 1. Modernization & Behavioral Preservation
- **Constraint:** Modernize the existing application while preserving its current behavior. Do not replace the application wholesale or remove existing features.
- **Operational Rule:** Perform incremental, test-backed refactoring. Output formats, alert calculations, and core sensor/device workflows must remain behaviorally equivalent to the legacy system.
- **Reference:** See `docs/research/01_legacy_system_analysis.md` for baseline legacy behavior and identified code smells.

#### 2. Multi-File Modular Architecture
- **Constraint:** Refactor into multiple logically organized source files. A submission remaining in one file is disqualified.
- **Operational Rule:** Decompose the God-class into clean packages under `src/main/java/` (e.g., `model/`, `sensor/`, `actuator/`, `alert/`, `reporting/`, `concurrency/`). Keep classes focused with single responsibilities (SRP).
- **Reference:** See `docs/research/03_refactoring_quality.md` for modular package-by-component layout.

#### 3. Concurrency, Thread Safety & Reliability
- **Constraint:** Address concurrency, design, and maintainability issues.
- **Operational Rule:**
  - Eliminate all shared mutable state, lost updates, race conditions, and potential deadlocks.
  - Use standard `java.util.concurrent` primitives (`ConcurrentHashMap`, `BlockingQueue`, `LongAdder`, atomic variables, virtual threads or structured executors).
  - Implement graceful shutdown routines (`Runtime.getRuntime().addShutdownHook`) to stop sensor simulation and worker threads cleanly.
- **Reference:** See `docs/research/05_concurrency_analysis.md` (defect catalog) and `docs/research/06_concurrency_improvements.md` (remediation strategies).

#### 4. Extensibility & Dynamic Crop Management (OCP)
- **Constraint:** Support adding and removing crops with minimal architectural changes.
- **Operational Rule:**
  - Decouple crop profile definitions from sensor validation logic using a dynamic crop registry / factory pattern.
  - Adding a new crop profile (e.g., Chili, Spinach, Cucumber) or removing an existing one must require **zero changes** to the monitoring engine or alert evaluation loop.
- **Reference:** See `docs/research/02_solid_principles.md` for mathematical subtyping and dynamic registry design.

#### 5. SOLID Principles & Design Pattern Justification
- **Constraint:** Apply SOLID principles and justify any design patterns used.
- **Operational Rule:**
  - Introduce patterns only where they solve a concrete design flaw (e.g., **Observer** for sensor updates & alerts, **Strategy** for environment control actions, **Factory/Registry** for crop profiles).
  - Every pattern must be clearly documented with rationale for inclusion in the final technical report (`README.pdf`). Avoid unnecessary complexity or "patternitis".
- **Reference:** See `docs/research/04_design_pattern_usage.md` for pattern selection and defense.

#### 6. Build System & Executable Artifact
- **Constraint:** Keep a Maven project structure and support an executable JAR.
- **Operational Rule:**
  - Target environment: **Java 25+** and **Maven** (`pom.xml`).
  - No unauthorized external frameworks (e.g., do not add Spring Boot); rely on standard Java SE and permitted Maven plugins.
  - The build must produce a self-contained executable JAR named `run.jar` in `target/`.
  - Must run without IDE configuration: `java -jar target/run.jar`.

#### 7. Final Deliverables & Oral Presentation Readiness
- **Constraint:** Final deliverables include source, an executable `run.jar`, and a technical report. Check the specification for full submission requirements.
- **Operational Rule:**
  - Code must be clean, well-commented, and easily explainable so the team can confidently defend every architectural decision during the Week 12 presentation (5 min presentation + 2 min live demo).
  - Technical Report: 3–6 pages (`README.pdf`) covering legacy analysis, SOLID improvements, concurrency solutions, and design pattern justifications.
- **Reference:** See `docs/se301_project_specification.md` and `docs/RESEARCH_SYNTHESIS.md`.

---

### Verification Workflow (Run Before Concluding Any Task)
Agents must execute and verify the following commands before declaring any feature or refactoring task complete:
1. **Compile & Unit Test:** `mvn clean test`
2. **Build Executable JAR:** `mvn package`
3. **Smoke Verification:** `java -jar target/run.jar`
