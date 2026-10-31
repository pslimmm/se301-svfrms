package org.example.svfrms.demo;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class CropExtensionDemoTest {
    @TempDir Path directory;

    @Test
    void demoAddsProcessesAndRemovesRealCropWithoutChangingDefaultProfiles() throws Exception {
        var narration = new ArrayList<String>();
        try (var context = new SvfrmsApplicationContext(directory.resolve("demo.txt"), Clock.systemUTC(), ignored -> {}, 42)) {
            var original = context.cropRegistry().get("Tomato").orElseThrow();
            CropExtensionDemo.run(context, narration::add);
            assertThat(context.cropRegistry().size()).isEqualTo(6);
            assertThat(context.cropRegistry().get("Tomato")).contains(original);
            assertThat(context.cropRegistry().get("Kale")).isEmpty();
            assertThat(context.getChamberSummary("CH5").alerts()).isEqualTo(3);
            assertThat(context.getChamberSummary("CH5").mitigationActions()).isEqualTo(3);
            assertThat(context.resources().energyUsage()).isEqualTo(10);
            assertThat(context.alerts().getActiveAlertsSnapshot()).extracting(a -> a.message())
                    .containsExactly("CH5 Kale High Temp", "CH5 Low Light Level", "CH5 Low Nutrient Level");
            assertThat(narration).contains("Removed Kale: crop-specific alerts stop; global light/nutrient rules remain.",
                    "Demo complete: 3 alerts, 3 actions; no engine changes.");
        }
    }
}
