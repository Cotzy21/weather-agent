package no.weatheragent.nutrition;

import no.weatheragent.support.Retry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.util.List;

/**
 * Henter HELE Matvaretabellen (Mattilsynet) - ~2100 matvarer med næringsinnhold
 * per 100 g og vanlige porsjonsstørrelser. Gratis, uten nøkkel, og under åpen
 * NLOD-lisens (kommersiell bruk OK) - men KILDEN SKAL OPPGIS, det gjør
 * frontenden («Kilde: Matvaretabellen, Mattilsynet»).
 *
 * Fila er stor (~14 MB), så vi henter den ÉN gang og cacher lista i minnet
 * ("foods"-cachen) - null API-kall per brukersøk etterpå. Det er hele poenget:
 * søk og næringsberegning skjer lokalt.
 *
 * API-dok: https://www.matvaretabellen.no/api/
 */
@Component
public class MatvaretabellenClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 1000;

    private final RestClient http;

    public MatvaretabellenClient(RestClient.Builder builder) {
        this.http = builder
                .baseUrl("https://www.matvaretabellen.no/api")
                .build();
    }

    @Cacheable(value = "foods", sync = true)
    public List<FoodItem> allFoods() {
        return Retry.withRetry(MAX_ATTEMPTS, BACKOFF_MS, () -> http.get()
                .uri("/nb/foods.json")
                .exchange((request, response) -> {
                    // exchange bypasses retrieve's error handlers; retain 5xx retry behavior.
                    if (response.getStatusCode().isError()) {
                        new DefaultResponseErrorHandler().handleError(request.getURI(), request.getMethod(), response);
                    }
                    return MatvaretabellenParser.parse(response.getBody());
                }));
    }
}
