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
 * Henter navngitte fjelltopper innenfor et navngitt område fra OpenStreetMap via
 * Overpass-API-et. Gratis og uten API-nøkkel.
 *
 * Området matches på navn og kan være et fylke (admin_level 4), en kommune
 * (admin_level 7) eller en nasjonalpark (boundary=national_park) - f.eks.
 * "Møre og Romsdal", "Stranda" eller "Jotunheimen nasjonalpark".
 *
 * API-dok: https://wiki.openstreetmap.org/wiki/Overpass_API
 */
@Component
public class OverpassClient {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";

    // Overpass er ofte travel og svarer 504. Prøv noen ganger med økende pause.
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 2000;

    /**
     * %s = områdenavn (settes inn to ganger). Matcher fylke/kommune
     * (boundary=administrative, admin_level 4 eller 7) ELLER nasjonalpark, og
     * henter alle navngitte natural=peak-noder i området.
     */
    private static final String QUERY_TEMPLATE = """
            [out:json][timeout:90];
            (
              area["name"="%s"]["boundary"="administrative"]["admin_level"~"^(4|7)$"];
              area["name"="%s"]["boundary"="national_park"];
            )->.omr;
            node(area.omr)["natural"="peak"]["name"];
            out;
            """;

    /** Maks antall ruter vi returnerer (etter dedup på navn) for et helt område. */
    private static final int MAX_TRAILS = 25;

    private final RestClient http;

    public OverpassClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    /** Alle navngitte topper i et område (fylke, kommune eller nasjonalpark), matchet på navn. */
    public List<Peak> peaksInArea(String areaName) {
        String query = QUERY_TEMPLATE.formatted(areaName, areaName);

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
            // Navngitte fot-/turruter (relasjoner) OG navngitte stier (ways). Mange
            // norske merkede turer ligger som highway=path med navn, ikke som
            // route=hiking-relasjoner, så vi tar med begge for bedre dekning.
            around.append(String.format(Locale.ROOT,
                    "  relation(around:%d,%f,%f)[\"route\"~\"hiking|foot\"][\"name\"];%n",
                    radiusMeters, c.latitude(), c.longitude()));
            around.append(String.format(Locale.ROOT,
                    "  way(around:%d,%f,%f)[\"highway\"~\"path|footway\"][\"name\"];%n",
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
