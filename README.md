# se301-svfrms
Singapore Vertical Farming Research Management System

from root:

```
mvn package
java -jar target/svfrms-1.0.0.jar
```

Requires Java 25+ and Maven, with `JAVA_HOME` pointing to the JDK.

Run the legacy behavior tests:

```
mvn test
```

For a fresh build including tests, run `mvn clean package`. Each test owns a fresh
application; concurrency tests explicitly coordinate their workers. JUnit test
methods remain sequential. Archive-writing tests use temporary working directories.

Stage reports cover the [behavior baseline](docs/stage-1-validation.md),
[typed control requests](docs/stage-2-validation.md),
[instance-owned state](docs/stage-3-validation.md),
[supervised workers](docs/stage-4-validation.md),
[immutable catalog and pure evaluation](docs/stage-5-validation.md),
[simulation and reporting boundaries](docs/stage-6-validation.md),
[crop extension workflows](docs/stage-7-validation.md), and
[final integration and packaging](docs/stage-8-validation.md).
The current clean build passes 300 tests, including unchecked report-writer
regressions and graceful-close coverage. The standalone report helper prints
failures and returns; supervised runs fail if the final report cannot be saved,
and announce completion only after a successful save. The
[refactoring plan](docs/refactoring-plan.md) describes the target architecture
and the completed stages; the validation records document remaining limitations.

The optional [Kale demonstration](docs/stage-7-validation.md#running-the-demonstration)
adds a fifth chamber through catalog data. Its thresholds are synthetic example
inputs; the default CLI still runs the original four chambers.

Submission generation is outside the current follow-up scope. Existing files in
`output/` predate the latest fixes. The optional submission workflow, if needed
later, starts after running `mvn clean package`:

```powershell
python -m pip install --target .tools/pdf -r scripts/pdf-requirements.txt
python scripts/build_report.py
python scripts/package_submission.py
python scripts/verify_submission.py
```

Enter the team members' names in `docs/report-metadata.json` before packaging.
The generated `output/svfrms-group-8.zip` contains source, tests, `run.jar`, and
the technical report under both specification filenames (`README.pdf` and
`report.pdf`). The report has a Group 8 cover, followed
by five technical pages. Editable content and metadata live in `docs/`;
re-render and visually review the report after edits. Python/PDF packages are
authoring tools only. The executable requires Java 25+ and no external libraries.

Run `java -jar run.jar` from the extracted submission directory, or
`java -cp run.jar org.example.CropExtensionDemo` for the optional demo. Each run
replaces `archive/daily_report.txt` in its working directory. Use separate working
directories to retain both outputs. The [Stage 8 record](docs/stage-8-validation.md)
describes validation and the remaining concurrency/persistence limits.
