package org.example.svfrms.demo;

import org.example.svfrms.domain.crop.CropProfile;
import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;

/** Optional crop-extension demonstration. It does not change the default application run. */
public final class CropExtensionDemo {
    private CropExtensionDemo() { }

    public static void main(String[] args) throws Exception {
        try (var context = new SvfrmsApplicationContext(Path.of("archive", "crop-demo-report.txt"),
                Clock.systemDefaultZone(), System.out::println, 42)) {
            run(context, System.out::println);
            context.generateDailyReport();
            context.archiveAlerts();
            context.saveArchiveReport();
        }
    }

    static void run(SvfrmsApplicationContext context, Consumer<String> narration) throws InterruptedException {
        CropProfile template = context.cropRegistry().get("Tomato").orElseThrow();
        CropProfile kale = new CropProfile("Kale", template.temperature(), template.humidity(),
                template.soilMoisture(), template.light(), template.nutrient(),
                template.mitigationResourceCosts(), template.enabledSensors());
        narration.accept("Registering Kale with illustrative Tomato-equivalent thresholds, not agronomic guidance.");
        context.cropRegistry().register(kale);
        context.processSensor("CH5", "Kale", "Temperature", 29);
        context.awaitControls(Duration.ofSeconds(5));
        context.cropRegistry().unregister("Kale");
        narration.accept("Removed Kale: crop-specific alerts stop; global light/nutrient rules remain.");
        context.processSensor("CH5", "Kale", "Temperature", 29);
        context.processSensor("CH5", "Kale", "Light", 499);
        context.processSensor("CH5", "Kale", "Nutrient", 39);
        context.awaitControls(Duration.ofSeconds(5));
        var summary = context.getChamberSummary("CH5");
        narration.accept("Demo complete: " + summary.alerts() + " alerts, " + summary.mitigationActions()
                + " actions; no engine changes.");
    }
}
