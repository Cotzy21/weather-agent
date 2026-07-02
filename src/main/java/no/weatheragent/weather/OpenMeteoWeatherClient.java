package no.weatheragent.weather;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;
import no.weatheragent.support.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Henter vaervarsel fra Open-Meteo - fallback-kilden vaar naar MET feiler eller
 * ikke dekker datoene (MET stopper paa ~9-10 dager, Open-Meteo gir 16).
 * Gratis for ikke-kommersiell bruk, uten API-noekkel, globalt.
 *
 * API-dok: https://open-meteo.com/en/docs
 */
@Component
public class OpenMeteoWeatherClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 500;

    private static final int FORECAST_DAYS = 16;

    private final RestClient http;

    public OpenMeteoWeatherClient(RestClient.Builder builder) {
        this.http = builder
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }

    /** Henter timesvarsel 16 dager fram for et sted. */
    public Forecast fetch(Location location) {
        JsonNode root = Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.get()
                .uri(uri -> uri.path("/forecast")
                        .queryParam("latitude", location.latitude())
                        .queryParam("longitude", location.longitude())
                        .queryParam("hourly", "temperature_2m,precipitation,wind_speed_10m")
                        .queryParam("wind_speed_unit", "ms")
                        .queryParam("forecast_days", FORECAST_DAYS)
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(JsonNode.class));

        return OpenMeteoForecastParser.parse(location, root);
    }
}
