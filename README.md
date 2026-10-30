# se301-svfrms
Singapore Vertical Farming Research Management System, refactored from the single-file legacy `SVFRMS.java`
into a modular, thread-safe Maven project.

## Build and run (Java 25+)

```
mvn clean package          # compiles, runs all tests, produces target/run.jar
java -jar target/run.jar   # same run as the legacy program (CH1 Tomato, CH2 Strawberry, CH3 Lettuce, CH4 Chili)
```

`java -version` must report 25 or newer; the jar is compiled for Java 25 and older JVMs will refuse it.

Useful options (`java -jar target/run.jar --help`):

| Option | Meaning |
|---|---|
| `--list-crops` | show every registered crop profile |
| `--chambers FILE` | chamber layout, one `CHx=Crop` per line |
| `--crops FILE` | extra crop profiles (same format as `src/main/resources/crops.properties`) |
| `--seed N --sequential` | repeatable, single-threaded run |
| `--all-reports` | also print weekly crop, water, energy and nutrient reports |

## Adding or removing a crop
Edit `src/main/resources/crops.properties` (or pass `--crops myfile.properties`). No Java changes needed.

```
kale.name=Kale
kale.temperature.min=15
kale.temperature.max=24
kale.soil.min=50
```

Then grow it: put `CH5=Kale` in a chambers file and run with `--chambers thatfile`.

## Project map
| Path | What |
|---|---|
| `src/main/java/org/example/svfrms/` | refactored code (packages: app, model, crop, chamber, sensor, alert, device, control, metrics, report, console, service, concurrency, config) |
| `src/test/java/org/example/legacy/` | untouched legacy code (package renamed) + characterization harness + bug demos |
| `src/test/resources/golden/` | output recorded from the legacy code; the new code must match it byte-for-byte |
| `docs/legacy/SVFRMS.java` | the original file, for reference |
| `docs/legacy-analysis.md` | responsibilities, smells, SOLID violations, concurrency issues |
| `docs/concurrency.md` | before/after table of every concurrency fix |
| `docs/design-patterns.md` | each pattern used and the problem it solves |
| `ASSUMPTIONS.md` | decisions we made where the brief was unclear |
| `STUDY_GUIDE.md` | class-by-class explanations and likely examiner questions |
