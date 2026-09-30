package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.security.SlidingWindowLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Slår opp et produkt på strekkode hos Open Food Facts (åpen database, ODbL: kilden skal oppgis, det gjør
 * bunnteksten i appen). Gratis og uten nøkkel, men de ber om en identifiserende User-Agent og at vi ikke
 * overbelaster dem, så vi bruker samme User-Agent som MET, har korte tidsavbrudd og en global grense på
 * {@value #MAX_PER_MINUTE} oppslag i minuttet.
 *
 * Funnet og ikke-funnet caches (begrenset, se CacheConfig). Feil (nettverk, 5xx, grensen nådd) kastes og caches
 * ikke, så et midlertidig avbrudd ikke gjemmer et produkt i seks timer.
 *
 * API-dok: https://openfoodfacts.github.io/openfoodfacts-server/api/
 */
@Component
public class OpenFoodFactsClient {

    /** Kastes når oppslaget ikke kunne gjøres nå (grensen nådd eller tjenesten nede). */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final int MAX_PER_MINUTE = 60;
    private static final String FIELDS = "product_name,generic_name,brands,nutriments,serving_quantity";

    private final RestClient http;
    private final SlidingWindowLimiter limiter;

    @Autowired
    public OpenFoodFactsClient(RestClient.Builder builder, @Value("${met.user-agent}") String userAgent) {
        this(builder
                .baseUrl("https://world.openfoodfacts.org/api/v2")
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                // Et strekkodeoppslag skjer mens brukeren står med telefonen: gi opp raskt heller enn å vente 90 s.
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(3)).withReadTimeout(Duration.ofSeconds(6))))
                .build(), Clock.systemUTC(), MAX_PER_MINUTE);
    }

    /** For tester: ferdig bygd klient (f.eks. mot en mock-server), klokke og grense. */
    OpenFoodFactsClient(RestClient http, Clock clock, int perMinute) {
        this.http = http;
        this.limiter = new SlidingWindowLimiter(perMinute, 60_000L, clock);
    }

    /** Produktet, eller tom når Open Food Facts ikke kjenner strekkoden (eller dataene er ubrukelige). */
    @Cacheable("off-products")
    public Optional<OpenFoodFactsProduct> lookup(String barcode) {
        if (limiter.tryAcquire("*") > 0) {
            throw new UnavailableException("Open Food Facts-grensen er nådd", null);
        }
        try {
            JsonNode root = http.get()
                    .uri("/product/{code}.json?fields=" + FIELDS, barcode) // FIELDS er en fast, trygg streng (bare bokstaver, komma, understrek)
                    .retrieve()
                    .body(JsonNode.class);
            return OpenFoodFactsParser.parse(root);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new UnavailableException("Open Food Facts svarte ikke", e);
        }
    }
}
