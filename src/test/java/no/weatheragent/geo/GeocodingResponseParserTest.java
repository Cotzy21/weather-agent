package no.weatheragent.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeocodingResponseParserTest {

    private static final String SAMPLE_JSON = """
            {
              "results": [
                { "name": "Stranda", "latitude": 62.3083, "longitude": 6.9333, "country_code": "NO", "admin1": "More og Romsdal" },
                { "name": "Stranda", "latitude": 59.9,    "longitude": 10.7,   "country_code": "NO", "admin1": "Akershus" },
                { "name": "Stranda", "latitude": 45.0,    "longitude": 12.0,   "country_code": "IT", "admin1": "Veneto" }
              ]
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsMatchesFromAllCountries() throws Exception {
        List<Location> matches = GeocodingResponseParser.parse(mapper.readTree(SAMPLE_JSON));

        assertEquals(3, matches.size());
        assertTrue(matches.stream().allMatch(l -> l.name().equals("Stranda")));
    }

    @Test
    void mapsCoordinatesFromFirstMatch() throws Exception {
        Location first = GeocodingResponseParser.parse(mapper.readTree(SAMPLE_JSON)).getFirst();

        assertEquals("Stranda", first.name());
        assertEquals(62.3083, first.latitude());
        assertEquals(6.9333, first.longitude());
    }

    @Test
    void returnsEmptyListWhenNoResults() throws Exception {
        List<Location> matches = GeocodingResponseParser.parse(mapper.readTree("{}"));

        assertTrue(matches.isEmpty());
    }
}
