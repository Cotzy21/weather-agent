package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;
import no.weatheragent.support.Retry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Henter navngitte fjelltopper innenfor et norsk fylke fra OpenStreetMap via
 * Overpass-API-et. Gratis og uten API-nøkkel.
 *
 * Spørringen er parametrisert på fylkesnavn (admin_level 4), så den virker for
 * hvilket som helst fylke - "Møre og Romsdal", "Vestland", "Troms" osv.
 *
 * API-dok: https://wiki.openstreetmap.org/wiki/Overpass_API
 */
@Component
public class OverpassClient {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";

    // Overpass er ofte travel og svarer 504. Prøv noen ganger med økende pause.
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 2000;

    /** %s = fylkesnavn. Henter alle navngitte natural=peak-noder i fylket. */
    private static final String QUERY_TEMPLATE = """
            [out:json][timeout:90];
            area["name"="%s"]["admin_level"="4"]->.fylke;
            node(area.fylke)["natural"="peak"]["name"];
            out;
            """;

    /** Maks antall ruter vi returnerer (etter dedup på navn) for et helt område. */
    private static final int MAX_TRAILS = 25;

    private final RestClient http;

    public OverpassClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    /** Alle navngitte topper i et fylke. */
    public List<Peak> peaksInCounty(String countyName) {
        String query = QUERY_TEMPLATE.formatted(countyName);

        JsonNode root = Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.post()
                .uri(ENDPOINT)
                .contentType(MediaType.TEXT_PLAIN)
                .body(query)
                .retrieve()
                .body(JsonNode.class));

        return OverpassPeakParser.parse(root);
    }

    /**
     * Navngitte merkede turruter innenfor {@code radiusMeters} av ETT ELLER FLERE
     * punkter (f.eks. topp-10-stedene), hentet i én union-spørring så vi slipper
     * et kall per sted. Deduplisert på navn og begrenset til {@link #MAX_TRAILS}.
     *
     * NB: koordinatene må formateres med punktum (Locale.ROOT), ellers ville
     * norsk lokal-format gi komma og ødelegge Overpass-spørringen.
     */
    public List<Trail> trailsNear(List<Location> centers, int radiusMeters) {
        if (centers.isEmpty()) {
            return List.of();
        }

        StringBuilder around = new StringBuilder();
        for (Location c : centers) {
            around.append(String.format(Locale.ROOT,
                    "  relation(around:%d,%f,%f)[\"route\"=\"hiking\"][\"name\"];%n",
                    radiusMeters, c.latitude(), c.longitude()));
        }
        String query = "[out:json][timeout:90];\n(\n" + around + ");\nout center tags;\n";

        JsonNode root = Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.post()
                .uri(ENDPOINT)
                .contentType(MediaType.TEXT_PLAIN)
                .body(query)
                .retrieve()
                .body(JsonNode.class));

        // Behold første forekomst av hvert navn, så samme rute ikke listes flere ganger.
        Map<String, Trail> byName = new LinkedHashMap<>();
        for (Trail trail : OverpassTrailParser.parse(root)) {
            byName.putIfAbsent(trail.name().toLowerCase(Locale.ROOT), trail);
        }
        return byName.values().stream().limit(MAX_TRAILS).toList();
    }
}
