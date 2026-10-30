package org.example.svfrms.crop;

import java.util.List;
import java.util.Optional;

/**
 * Where crop profiles live at runtime (Registry pattern). Other classes depend on this interface,
 * not on how or where the profiles are stored (Dependency Inversion).
 */
public interface CropRegistry {

    /** Adds a crop, or replaces an existing crop with the same name. */
    void register(CropProfile profile);

    /** Removes a crop. Returns false if it was not registered. */
    boolean remove(String cropName);

    Optional<CropProfile> find(String cropName);

    /** All crops sorted by name. */
    List<CropProfile> all();
}
