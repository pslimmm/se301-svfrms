package org.example.svfrms.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.example.svfrms.config.ConfigFile;
import org.example.svfrms.model.Breach;
import org.example.svfrms.model.ResourceCost;
import org.example.svfrms.model.SensorType;
import org.junit.jupiter.api.Test;

class CropProfileLoaderTest {

    private final List<CropProfile> bundled =
            CropProfileLoader.load(ConfigFile.fromClasspath("crops.properties"), "crops.properties");

    private CropProfile crop(String name) {
        return bundled.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void bundledFileHasLegacyAndNewCrops() {
        List<String> names = bundled.stream().map(CropProfile::name).sorted().toList();
        assertEquals(List.of("Bell Pepper", "Bok Choy", "Chili", "Cucumber", "Eggplant", "Kale",
                "Lettuce", "Spinach", "Strawberry", "Tomato"), names);
    }

    @Test
    void tomatoMatchesLegacyRules() {
        CropProfile tomato = crop("Tomato");
        assertEquals(22, tomato.threshold(SensorType.TEMPERATURE).orElseThrow().range().min());
        assertEquals(28, tomato.threshold(SensorType.TEMPERATURE).orElseThrow().range().max());
        assertEquals(new ResourceCost(0, 10), tomato.costFor(SensorType.TEMPERATURE, Breach.HIGH));
        assertEquals(new ResourceCost(0, 15), tomato.costFor(SensorType.TEMPERATURE, Breach.LOW));
        assertEquals(new ResourceCost(50, 0), tomato.costFor(SensorType.SOIL_MOISTURE, Breach.LOW));
        // legacy only checked "soil < 45" for Tomato, never "too wet"
        assertFalse(tomato.threshold(SensorType.SOIL_MOISTURE).orElseThrow().range().isAbove(1_000));
    }

    @Test
    void cropsWithoutAHumidityRuleInLegacyHaveNoneHere() {
        assertTrue(crop("Lettuce").threshold(SensorType.HUMIDITY).isEmpty());
        assertTrue(crop("Chili").threshold(SensorType.SOIL_MOISTURE).isEmpty());
    }

    @Test
    void newCropComesPurelyFromData() {
        List<CropProfile> loaded = CropProfileLoader.load(Map.of(
                "basil.name", "Basil",
                "basil.temperature.min", "20",
                "basil.temperature.max", "30",
                "basil.soil.low.water", "12",
                "basil.soil.min", "40"), "test");
        CropProfile basil = loaded.getFirst();
        assertEquals("Basil", basil.name());
        assertEquals(new ResourceCost(12, 0), basil.costFor(SensorType.SOIL_MOISTURE, Breach.LOW));
    }

    @Test
    void badNumberGivesAHelpfulMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CropProfileLoader.load(
                Map.of("kale.name", "Kale", "kale.temperature.min", "cold"), "my-crops.properties"));
        assertTrue(e.getMessage().contains("kale.temperature.min"));
        assertTrue(e.getMessage().contains("my-crops.properties"));
    }

    @Test
    void minAboveMaxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CropProfileLoader.load(
                Map.of("x.name", "X", "x.temperature.min", "30", "x.temperature.max", "10"), "test"));
    }

    @Test
    void registryAddsAndRemovesCrops() {
        CropRegistry registry = new InMemoryCropRegistry();
        bundled.forEach(registry::register);
        assertTrue(registry.remove("Kale"));
        assertTrue(registry.find("Kale").isEmpty());
        assertFalse(registry.remove("Kale"));
        assertEquals(9, registry.all().size());
    }
}
