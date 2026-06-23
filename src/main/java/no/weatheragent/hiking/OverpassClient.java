package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.support.Retry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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
}
