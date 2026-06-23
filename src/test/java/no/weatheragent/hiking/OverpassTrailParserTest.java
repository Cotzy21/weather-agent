package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverpassTrailParserTest {

    private static final String SAMPLE_JSON = """
            {
              "elements": [
                {
                  "type": "relation",
                  "center": { "lat": 58.99, "lon": 6.19 },
                  "tags": { "route": "hiking", "name": "Preikestolen Roundtrip", "network": "lwn" }
                },
                {
                  "type": "relation",
                  "center": { "lat": 59.00, "lon": 6.20 },
                  "tags": { "route": "hiking", "name": "Signatur Lysefjorden rundt",
                            "network": "nwn", "operator": "Den Norske Turistforening" }
                },
                {
                  "type": "relation",
                  "center": { "lat": 59.01, "lon": 6.21 },
                  "tags": { "route": "hiking", "network": "rwn" }
                },
                {
                  "type": "relation",
                  "tags": { "route": "hiking", "name": "Mangler senter" }
                }
              ]
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsOnlyNamedTrailsWithCenter() throws Exception {
        List<Trail> trails = OverpassTrailParser.parse(mapper.readTree(SAMPLE_JSON));

        // Den uten navn og den uten senter skal droppes -> 2 igjen.
        assertEquals(2, trails.size());
        assertTrue(trails.stream().allMatch(t -> !t.name().isBlank()));
    }

    @Test
    void mapsFieldsIncludingOperator() throws Exception {
        Trail dnt = OverpassTrailParser.parse(mapper.readTree(SAMPLE_JSON)).stream()
                .filter(t -> t.name().equals("Signatur Lysefjorden rundt"))
                .findFirst().orElseThrow();

        assertEquals("nwn", dnt.network());
        assertEquals("Den Norske Turistforening", dnt.operator());
        assertEquals(59.00, dnt.latitude());
        assertEquals(6.20, dnt.longitude());
    }

    @Test
    void missingOperatorBecomesEmpty() throws Exception {
        Trail roundtrip = OverpassTrailParser.parse(mapper.readTree(SAMPLE_JSON)).getFirst();

        assertEquals("Preikestolen Roundtrip", roundtrip.name());
        assertEquals("", roundtrip.operator());
    }
}
