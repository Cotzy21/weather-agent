package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;
import no.weatheragent.support.Retry;
import org.springframework.cache.annotation.Cacheable;
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
 * Området kan være et administrativt område (fylke/kommune/delstat/provins,
 * admin_level 4-8) eller en nasjonalpark (boundary=national_park), hvor som
 * helst i verden - f.eks. "Møre og Romsdal", "Stranda" eller "Tirol". Med en
 * landkode scopes søket til landet (unngår navnekollisjoner); uten søkes det
 * globalt på navn. Selve spørringene bygges i {@link OverpassQueries}.
 *
 * API-dok: https://wiki.openstreetmap.org/wiki/Overpass_API
 */
@Component
public class OverpassClient {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";

    // Overpass er ofte travel og svarer 504. Prøv noen ganger med økende pause.
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 2000;

    /** Maks antall ruter vi returnerer (etter dedup på navn) for et helt område. */
    private static final int MAX_TRAILS = 25;

    private final RestClient http;

    public OverpassClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    /**
     * Alle navngitte topper i et område (administrativt eller nasjonalpark).
     * Med landkode søkes det i landet først; gir det ingen treff (f.eks. feil
     * landkode fra tolkeren), prøves ett globalt navnesøk før vi gir opp.
     */
    @Cacheable(value = "peaks", key = "#areaName.toLowerCase() + ':' + #countryCode")
    public List<Peak> peaksInArea(String areaName, String countryCode) {
        List<Peak> peaks = OverpassPeakParser.parse(
                fetch(OverpassQueries.peaks(areaName, countryCode)));
        if (peaks.isEmpty() && countryCode != null) {
            peaks = OverpassPeakParser.parse(fetch(OverpassQueries.peaks(areaName, null)));
        }
        return peaks;
    }

    private JsonNode fetch(String query) {
        return Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.post()
                .uri(ENDPOINT)
                .contentType(MediaType.TEXT_PLAIN)
                .body(query)
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * Navngitte merkede turruter innenfor {@code radiusMeters} av ETT ELLER FLERE
     * punkter (f.eks. topp-10-stedene), hentet i én union-spørring så vi slipper
     * et kall per sted. Deduplisert på navn og begrenset til {@link #MAX_TRAILS}.
     *
     * NB: koordinatene må formateres med punktum (Locale.ROOT), ellers ville
     * norsk lokal-format gi komma og ødelegge Overpass-spørringen.
     */
    @Cacheable("trails")
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

        // Behold første forekomst av hvert navn, så samme rute ikke listes flere ganger.
        Map<String, Trail> byName = new LinkedHashMap<>();
        for (Trail trail : OverpassTrailParser.parse(fetch(query))) {
            byName.putIfAbsent(trail.name().toLowerCase(Locale.ROOT), trail);
        }
        return byName.values().stream().limit(MAX_TRAILS).toList();
    }

    /**
     * Alle navngitte turruter/stier innenfor et område (administrativt eller
     * nasjonalpark), deduplisert på navn. Brukes når brukeren vil rangere selve
     * turrutene etter vær. Samme land-scope + globalt fallback som peaksInArea.
     */
    @Cacheable(value = "trailsInArea", key = "#areaName.toLowerCase() + ':' + #countryCode")
    public List<Trail> trailsInArea(String areaName, String countryCode) {
        List<Trail> trails = OverpassTrailParser.parse(
                fetch(OverpassQueries.trailsInArea(areaName, countryCode)));
        if (trails.isEmpty() && countryCode != null) {
            trails = OverpassTrailParser.parse(fetch(OverpassQueries.trailsInArea(areaName, null)));
        }

        Map<String, Trail> byName = new LinkedHashMap<>();
        for (Trail trail : trails) {
            byName.putIfAbsent(trail.name().toLowerCase(Locale.ROOT), trail);
        }
        return List.copyOf(byName.values());
    }
}
