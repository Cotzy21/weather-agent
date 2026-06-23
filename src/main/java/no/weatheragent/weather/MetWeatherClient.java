package no.weatheragent.weather;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;
import no.weatheragent.support.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Henter vaervarsel fra MET (Meteorologisk institutt) sitt LocationForecast-API.
 *
 * NB: MET KREVER en identifiserende User-Agent med kontaktinfo, ellers blir
 * forespoerselen blokkert. Den settes i application.properties (met.user-agent).
 *
 * API-dok: https://api.met.no/weatherapi/locationforecast/2.0/documentation
 */
@Component
public class MetWeatherClient {

    // MET kalles én gang per kandidat, så vi holder pausen kort ved flyktige feil.
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 500;

    private final RestClient http;

    public MetWeatherClient(RestClient.Builder builder,
                            @Value("${met.user-agent}") String userAgent) {
        this.http = builder
                .baseUrl("https://api.met.no/weatherapi/locationforecast/2.0")
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    /**
     * Henter komplett vaervarsel for et sted.
     * MET anbefaler aa kutte koordinater til 4 desimaler (bedre caching hos dem).
     */
    public Forecast fetch(Location location) {
        double lat = round4(location.latitude());
        double lon = round4(location.longitude());

        JsonNode root = Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.get()
                .uri(uri -> uri.path("/compact")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        return MetForecastParser.parse(location, root);
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
