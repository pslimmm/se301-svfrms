# Demo script (2 minutes)

## Before the presentation (not timed)
1. **Java 25 on the PATH.** `java -version` must show 25+. (On the dev laptop the default `java` is Java 8, which fails with
   `UnsupportedClassVersionError`.) In PowerShell, for the session only:
   ```powershell
   $env:JAVA_HOME = "C:\path\to\jdk-25"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   java -version
   ```
2. `mvn clean package` once, so `target/run.jar` exists and the Maven dependencies are cached (no downloads on stage).
3. Open a terminal in the project root, font size large, and clear the screen. Have `src/main/resources/crops.properties` open in an editor.
4. Delete any old `archive/` folder.

## On stage

| Time | Say | Do |
|---|---|---|
| 0:00 | "This is the refactored system running exactly like the legacy program: four chambers in parallel." | `java -jar target/run.jar` and scroll to the end: alerts, green control lines, daily report, "Archive report saved to". |
| 0:20 | "Crops are now data. Here's Kale, one of the four crops we added without touching Java." | Show the Kale block in `crops.properties`. Then `java -jar target/run.jar --list-crops` |
| 0:35 | "Let's grow Kale in a new chamber, CH5. The layout is a file too. The seed and sequential options give a repeatable run." | `java -jar target/run.jar --chambers demo/chambers.properties --seed 7 --sequential` and point at `[CH5][Kale][ALERT] CH5 Kale High Temp` |
| 0:55 | "Now a brand-new crop nobody has seen before: Basil. No rebuild and no code change. We just pass a file." | `type demo\basil.properties` (or `cat`), then `java -jar target/run.jar --crops demo/basil.properties --chambers demo/basil-chamber.properties --seed 7 --sequential` and point at `CH9 Basil ...` |
| 1:20 | "Finally, concurrency. The first test runs the **original** legacy code: watch the lost updates and the stuck thread. The second hammers our new classes with 16 threads." | `mvn -q test "-Dtest=LegacyConcurrencyDemoTest,ThreadSafetyStressTest"` and point at `[legacy-demo] lost updates: expected 80000000 L but legacy counted ...` and `interrupted legacy producer still blocked: true`. Then show green results for the new code. |
| 1:50 | "Plus golden tests prove our output is identical to the legacy output byte for byte. 49 tests in total. Back to the slides." | (Optional, if time) `mvn -q test -Dtest=GoldenRegressionTest` |

## If something goes wrong
* `UnsupportedClassVersionError`: wrong Java. Fix the PATH (step 1).
* No internet for `mvn test`: run `mvn -o -q test ...` (offline), which works after step 2.
* The race demo prints nothing: races are timing-based, so that test is *skipped* rather than failed when the race doesn't
  show up. Run it once more, or point to the recorded result in `docs/concurrency.md` (80,000,000 L expected → 11,368,500 L counted).
* Output too long to read: add `--cycles 3` to any run.
