package no.weatheragent.region;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifiserer at regions.json lastes riktig - uten Spring-kontekst.
 */
class RegionRegistryTest {

    private final RegionRegistry registry = new RegionRegistry(new ObjectMapper());

    @Test
    void loadsSunnmoreWithPlaces() {
        Optional<Region> region = registry.find("Sunnmøre");

        assertTrue(region.isPresent());
        assertFalse(region.get().places().isEmpty());
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertTrue(registry.find("sunnmøre").isPresent());
        assertTrue(registry.find("  SUNNMØRE  ").isPresent());
    }

    @Test
    void unknownRegionGivesEmpty() {
        assertTrue(registry.find("Atlantis").isEmpty());
    }

    @Test
    void placesHaveCoordinates() {
        Region sunnmore = registry.find("Sunnmøre").orElseThrow();

        sunnmore.places().forEach(place -> {
            assertFalse(place.name().isBlank());
            // Norge ligger grovt sett mellom 57-72 grader nord
            assertTrue(place.latitude() > 57 && place.latitude() < 72,
                    "Mistenkelig breddegrad for " + place.name());
        });
    }

    @Test
    void knowsMultipleRegions() {
        assertEquals(2, registry.regionNames().size());
        assertTrue(registry.regionNames().contains("Lofoten"));
    }
}
