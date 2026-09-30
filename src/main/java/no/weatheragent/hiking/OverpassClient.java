package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    /**
     * Offentlige Overpass-instanser med full verdensdatabase, i prioritert
     * rekkefølge. Hovedinstansen (overpass-api.de) er ofte overbelastet (429/504);
     * da prøver vi neste speil i stedet for å gi opp.
     */
    static final List<String> ENDPOINTS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter");

    /**
     * Tidsstyring. Overpass svarer ofte sakte eller ikke i det hele tatt. Uten disse grensene kunne ett oppslag ta
     * 3 speil x 2 forsøk x 90 s, langt over vertens tidsgrense (Render kutter etter ca. 100 s og gir en tom 502).
     * Nå: hvert kall gir opp etter 30 s, neste speil settes i gang PARALLELT hvis det første ikke har svart etter 6 s
     * (og med en gang hvis det feiler), det første svaret vinner, og hele oppslaget har et tak på 35 s.
     */
    static final long HEDGE_DELAY_MS = 6_000;
    static final long TOTAL_BUDGET_MS = 35_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Selve kallene går på virtuelle tråder, så en hengende tilkobling ikke tar en plattformtråd. */
    private static final ExecutorService ATTEMPTS = Executors.newVirtualThreadPerTaskExecutor();

    /** Maks antall ruter vi returnerer (etter dedup på navn) for et helt område. */
    private static final int MAX_TRAILS = 25;

    private final RestClient http;
    private final List<String> endpoints;
    private final long hedgeDelayMs;
    private final long totalBudgetMs;

    @Autowired
    public OverpassClient(RestClient.Builder builder) {
        this(builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT).withReadTimeout(READ_TIMEOUT)))
                .build(), ENDPOINTS, HEDGE_DELAY_MS, TOTAL_BUDGET_MS);
    }

    /** For tester: ferdig bygd klient (f.eks. mot en mock-server) og egne tidsgrenser. */
    OverpassClient(RestClient http, List<String> endpoints, long hedgeDelayMs, long totalBudgetMs) {
        this.http = http;
        this.endpoints = endpoints;
        this.hedgeDelayMs = hedgeDelayMs;
        this.totalBudgetMs = totalBudgetMs;
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

    /**
     * Kjør spørringen mot Overpass-instansene og ta det første gyldige svaret (se {@link #raceMirrors}). Kaster
     * {@link OverpassUnavailableException} når ingen svarer i tide, og andre 4xx (feil i VÅR spørring) kastes med en
     * gang, siden verken speilbytte eller nytt forsøk hjelper da.
     */
    private JsonNode fetch(String query) {
        return raceMirrors(endpoints, endpoint -> http.post()
                        .uri(endpoint)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(query)
                        .retrieve()
                        .body(JsonNode.class),
                hedgeDelayMs, totalBudgetMs);
    }

    /**
     * Prøver speilene i rekkefølge, men uten å vente på et som henger: feiler et (travelt/utilgjengelig) settes neste i
     * gang UMIDDELBART, og har ingen svart etter {@code hedgeDelayMs} settes neste i gang PARALLELT med de som
     * fortsatt venter. Første gyldige svar vinner. Gir opp med {@link OverpassUnavailableException} når alle har feilet
     * eller {@code totalBudgetMs} er brukt opp. Et kall som henger får gå ut på sin egen leseterskel i bakgrunnen.
     */
    static JsonNode raceMirrors(List<String> endpoints, Function<String, JsonNode> call, long hedgeDelayMs, long totalBudgetMs) {
        BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalBudgetMs);
        int started = 0;
        int finished = 0;
        RestClientException lastBusy = null;

        launch(endpoints.get(started++), call, events);
        while (true) {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMs <= 0) {
                throw new OverpassUnavailableException(lastBusy);
            }
            Object event;
            try {
                event = events.poll(Math.min(hedgeDelayMs, remainingMs), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OverpassUnavailableException(e);
            }
            if (event == null) { // ingen har svart ennå: sett neste speil i gang ved siden av (hedging)
                if (started < endpoints.size()) {
                    launch(endpoints.get(started++), call, events);
                }
                continue;
            }
            if (event instanceof JsonNode node) {
                return node;
            }
            RestClientException failure = (RestClientException) event;
            finished++;
            if (!isBusy(failure)) {
                throw failure;
            }
            lastBusy = failure;
            if (started < endpoints.size()) {
                launch(endpoints.get(started++), call, events);
            } else if (finished >= started) {
                throw new OverpassUnavailableException(lastBusy);
            }
        }
    }

    private static void launch(String endpoint, Function<String, JsonNode> call, BlockingQueue<Object> events) {
        ATTEMPTS.execute(() -> {
            try {
                JsonNode node = call.apply(endpoint);
                events.add(node != null ? node : new ResourceAccessException("Tomt svar fra Overpass"));
            } catch (RestClientException e) {
                events.add(e);
            } catch (RuntimeException e) {
                events.add(new ResourceAccessException("Overpass-kallet feilet: " + e.getClass().getSimpleName()));
            }
        });
    }

    /** Overbelastet/utilgjengelig instans: 429, 5xx eller nettverks-/timeout-feil. */
    private static boolean isBusy(RestClientException e) {
        if (e instanceof ResourceAccessException || e instanceof HttpServerErrorException) {
            return true;
        }
        return e instanceof HttpStatusCodeException status
                && status.getStatusCode().value() == 429;
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
     * Navngitte turruter/stier rundt ett sted MED linjegeometri, så kartet kan
     * tegne og highlighte selve stiene (detaljsiden). Egen cache: geometrien er
     * større enn senterpunktene og endrer seg nesten aldri.
     */
    @Cacheable(value = "trailGeometry", key = "#center.latitude() + ',' + #center.longitude() + ',' + #radiusMeters")
    public List<Trail> trailGeometriesNear(Location center, int radiusMeters) {
        return OverpassTrailParser.parseWithGeometry(fetch(
                OverpassQueries.trailGeometriesNear(center.latitude(), center.longitude(), radiusMeters)));
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
