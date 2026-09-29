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

    @Test
    void geometryResponseMergesSameNamedSegmentsAndSkipsClippedGaps() throws Exception {
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"elements":[
                  {"type":"way","tags":{"name":"Slogstien","highway":"path"},
                   "geometry":[{"lat":62.10,"lon":6.80},{"lat":62.11,"lon":6.81}]},
                  {"type":"way","tags":{"name":"slogstien","highway":"path"},
                   "geometry":[{"lat":62.11,"lon":6.81},null,{"lat":62.12,"lon":6.82}]},
                  {"type":"relation","tags":{"name":"Kyststien","route":"hiking","operator":"DNT"},
                   "members":[{"type":"way","geometry":[{"lat":62.0,"lon":6.0},{"lat":62.0,"lon":6.1}]},
                              {"type":"node"}]},
                  {"type":"way","tags":{"highway":"path"},
                   "geometry":[{"lat":1,"lon":1},{"lat":2,"lon":2}]}
                ]}
                """);

        var trails = OverpassTrailParser.parseWithGeometry(root);

        org.junit.jupiter.api.Assertions.assertEquals(2, trails.size());          // uten navn hoppes over
        var slog = trails.getFirst();
        org.junit.jupiter.api.Assertions.assertEquals("Slogstien", slog.name());
        org.junit.jupiter.api.Assertions.assertEquals(2, slog.lines().size());    // to segmenter slått sammen
        org.junit.jupiter.api.Assertions.assertEquals(2, slog.lines().get(1).size()); // null-hullet hoppet over
        var kyst = trails.get(1);
        org.junit.jupiter.api.Assertions.assertEquals("DNT", kyst.operator());
        org.junit.jupiter.api.Assertions.assertEquals(62.0, kyst.latitude(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(6.05, kyst.longitude(), 1e-9);
    }

    @Test
    void longLinesAreThinnedButKeepBothEnds() {
        var points = new java.util.ArrayList<no.weatheragent.route.RoutePoint>();
        for (int i = 0; i < 500; i++) {
            points.add(new no.weatheragent.route.RoutePoint(i, i));
        }

        var thin = OverpassTrailParser.thin(points, 60);

        org.junit.jupiter.api.Assertions.assertEquals(60, thin.size());
        org.junit.jupiter.api.Assertions.assertEquals(0, thin.getFirst().lat());
        org.junit.jupiter.api.Assertions.assertEquals(499, thin.getLast().lat());
    }
}
