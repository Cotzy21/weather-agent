package no.weatheragent.hiking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spørringsbyggingen er ren tekst-logikk og testes uten nettverk, som parserne.
 */
class OverpassQueriesTest {

    @Test
    void peaksWithoutCountrySearchesGloballyByName() {
        String q = OverpassQueries.peaks("Møre og Romsdal", null);

        assertTrue(q.contains("area[\"name\"=\"Møre og Romsdal\"][\"boundary\"=\"administrative\"]"));
        assertTrue(q.contains("[\"admin_level\"~\"^(4|5|6|7|8)$\"]"));
        assertTrue(q.contains("area[\"name\"=\"Møre og Romsdal\"][\"boundary\"=\"national_park\"]"));
        assertTrue(q.contains("node(area.omr)[\"natural\"=\"peak\"][\"name\"]"));
        assertFalse(q.contains("ISO3166-1"));
    }

    @Test
    void peaksWithCountryScopesLookupToThatCountry() {
        String q = OverpassQueries.peaks("Tirol", "AT");

        assertTrue(q.contains("area[\"ISO3166-1\"=\"AT\"][\"admin_level\"=\"2\"]->.land"));
        assertTrue(q.contains("rel(area.land)[\"name\"=\"Tirol\"][\"boundary\"=\"administrative\"]"));
        assertTrue(q.contains("rel(area.land)[\"name\"=\"Tirol\"][\"boundary\"=\"national_park\"]"));
        assertTrue(q.contains("map_to_area->.omr"));
    }

    @Test
    void trailsInAreaFetchesPathsAndRouteRelations() {
        String q = OverpassQueries.trailsInArea("Stranda", "NO");

        assertTrue(q.contains("way(area.omr)[\"highway\"~\"path|footway\"]->.w"));
        assertTrue(q.contains("rel(bw.w)[\"route\"~\"hiking|foot\"][\"name\"]"));
        assertTrue(q.contains("out center tags 600"));
    }

    @Test
    void areaNameIsEscapedSoItCannotBreakOutOfTheQuery() {
        String q = OverpassQueries.peaks("x\"]; node[\"amenity", null);

        // Anførselstegnet skal være escapet, så «navnet» forblir én streng.
        assertTrue(q.contains("area[\"name\"=\"x\\\"]; node[\\\"amenity\"]"));
    }

    @Test
    void escapeHandlesBackslashesQuotesAndControlCharacters() {
        assertEquals("a\\\\b", OverpassQueries.escape("a\\b"));
        assertEquals("a\\\"b", OverpassQueries.escape("a\"b"));
        assertEquals("a b", OverpassQueries.escape("a\nb"));
    }
}
