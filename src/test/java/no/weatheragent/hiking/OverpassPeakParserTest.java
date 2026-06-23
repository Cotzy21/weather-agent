package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverpassPeakParserTest {

    private static final String SAMPLE_JSON = """
            {
              "elements": [
                { "type": "node", "id": 1, "lat": 62.2147, "lon": 7.6944,
                  "tags": { "natural": "peak", "name": "Pyttegga", "ele": "1999" } },
                { "type": "node", "id": 2, "lat": 62.1847, "lon": 7.7345,
                  "tags": { "natural": "peak", "name": "Karitinden", "ele": "1982 m" } },
                { "type": "node", "id": 3, "lat": 62.0, "lon": 6.0,
                  "tags": { "natural": "peak", "name": "Utan hoyde" } },
                { "type": "node", "id": 4, "lat": 62.1, "lon": 6.1,
                  "tags": { "natural": "peak" } }
              ]
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesNamedPeaksAndSkipsUnnamed() throws Exception {
        List<Peak> peaks = OverpassPeakParser.parse(mapper.readTree(SAMPLE_JSON));

        // Node 4 mangler navn -> skal hoppes over.
        assertEquals(3, peaks.size());
        assertTrue(peaks.stream().allMatch(p -> !p.location().name().isBlank()));
    }

    @Test
    void readsCoordinatesAndElevation() throws Exception {
        Peak first = OverpassPeakParser.parse(mapper.readTree(SAMPLE_JSON)).getFirst();

        assertEquals("Pyttegga", first.location().name());
        assertEquals(62.2147, first.location().latitude());
        assertEquals(7.6944, first.location().longitude());
        assertEquals(1999.0, first.elevationMeters());
    }

    @Test
    void stripsUnitFromElevation() throws Exception {
        Peak karitinden = OverpassPeakParser.parse(mapper.readTree(SAMPLE_JSON)).get(1);

        // "1982 m" skal bli 1982.0
        assertEquals(1982.0, karitinden.elevationMeters());
    }

    @Test
    void missingElevationBecomesNull() throws Exception {
        Peak utenHoyde = OverpassPeakParser.parse(mapper.readTree(SAMPLE_JSON)).get(2);

        assertNull(utenHoyde.elevationMeters());
        assertTrue(utenHoyde.elevation().isEmpty());
    }
}
