package no.weatheragent.geo;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.support.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Slaar opp koordinater for et stedsnavn via Open-Meteo sitt geocoding-API.
 * Krever ingen API-noekkel.
 *
 * API-dok: https://open-meteo.com/en/docs/geocoding-api
 */
@Component
public class OpenMeteoGeocodingClient {

    // Som de andre eksterne klientene: proev paa nytt ved flyktige feil.
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 500;

    private final RestClient http;

    public OpenMeteoGeocodingClient(RestClient.Builder builder) {
        this.http = builder
                .baseUrl("https://geocoding-api.open-meteo.com/v1")
                .build();
    }

    /** Alle treff for et soek (globalt), sortert etter relevans slik Open-Meteo returnerer dem. */
    public List<Location> search(String name) {
        JsonNode root = Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("name", name)
                        .queryParam("count", 10)
                        .queryParam("language", "nb")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(JsonNode.class));

        return GeocodingResponseParser.parse(root);
    }

    /** Beste treff for et stedsnavn, om det finnes. */
    public Optional<Location> findFirst(String name) {
        return search(name).stream().findFirst();
    }
}
