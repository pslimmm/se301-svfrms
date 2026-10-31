# Group 3 two minute demonstration

Use Java25 and the freshly built target/run.jar. Run from the project root with cached Maven dependencies. Thresholds in the Kale example are illustrative Tomato-equivalent values, not agricultural guidance.

| Time | Action | Observable evidence |
| --- | --- | --- |
| 0:00-0:25 | java -jar target/run.jar | Four original chambers; report before archive; final saved report active0 |
| 0:25-0:55 | java -cp target/run.jar org.example.svfrms.demo.CropExtensionDemo | Kale registered and removed in one registry; CH5 has3 alerts and3 actions; energy10 |
| 0:55-1:25 | mvn "-Dtest=ActuatorServiceTest#closeDrainsAcceptedCommandsExactlyOnce" test | Positive admitted backlog drains exactly once; post-close submission rejected |
| 1:25-1:50 | Explain the same registry/evaluator/queue paths | No crop switch or engine edit; removed-crop global light/nutrient checks are deliberate |
| 1:50-2:00 | Show final-verification.json and ZIP contents | Current source, run.jar and editable DOCX report aliases; disclosed format/scope gaps |

Before rehearsal, run Maven checks and package verification once to warm dependencies. The presentation deck has a separate five-minute script. Do not run all unit tests during the live demonstration. If a live command fails, show the saved verification log and label it as prior evidence rather than a live success. Group members must perform and time their own rehearsal; names are currently deferred.
