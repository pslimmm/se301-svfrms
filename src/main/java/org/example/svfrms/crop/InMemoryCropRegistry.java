package org.example.svfrms.crop;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry backed by a ConcurrentHashMap, because chamber threads read it concurrently. */
public final class InMemoryCropRegistry implements CropRegistry {

    private final Map<String, CropProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public void register(CropProfile profile) {
        profiles.put(profile.name(), profile);
    }

    @Override
    public boolean remove(String cropName) {
        return profiles.remove(cropName) != null;
    }

    @Override
    public Optional<CropProfile> find(String cropName) {
        return Optional.ofNullable(profiles.get(cropName));
    }

    @Override
    public List<CropProfile> all() {
        return profiles.values().stream().sorted(Comparator.comparing(CropProfile::name)).toList();
    }
}
