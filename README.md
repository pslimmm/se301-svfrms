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
[simulation and reporting boundaries](docs/stage-6-validation.md), and
[crop extension workflows](docs/stage-7-validation.md).
The latest report records 280 passing tests. The
[refactoring plan](docs/refactoring-plan.md) describes the target architecture
and remaining stages.

The optional [Kale demonstration](docs/stage-7-validation.md#running-the-demonstration)
adds a fifth chamber through catalog data. Its thresholds are synthetic example
inputs; the default CLI still runs the original four chambers.
