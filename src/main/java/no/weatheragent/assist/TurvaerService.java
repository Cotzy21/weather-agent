package no.weatheragent.assist;

import no.weatheragent.advice.ClothingAdvisor;
import no.weatheragent.geo.Location;
import no.weatheragent.geo.OpenMeteoGeocodingClient;
import no.weatheragent.hiking.CandidateSelector;
import no.weatheragent.hiking.OverpassClient;
import no.weatheragent.hiking.Peak;
import no.weatheragent.hiking.Trail;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.interpret.QueryInterpreter;
import no.weatheragent.interpret.Target;
import no.weatheragent.interpret.TripType;
import no.weatheragent.ranking.BestWeatherFinder;
import no.weatheragent.ranking.DayWeather;
import no.weatheragent.ranking.RankedPlaceOverPeriod;
import no.weatheragent.ranking.ScoreWeights;
import no.weatheragent.ranking.WeatherScorer;
import no.weatheragent.weather.Forecast;
import no.weatheragent.weather.ResilientWeatherClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tur-assistentens kjerne: fra et fritekst-spørsmål til en rangering av steder
 * med best vær over perioden. Orkestrerer hele kjeden - tolkning (LLM) ->
 * turdata (OSM) -> vær/scoring (MET) - uten en eneste hardkodet region eller dato.
 *
 * Dette er bruksområdet (use case) et framtidig REST-endepunkt vil kalle;
 * CLI-/demo-runnerne er bare tynne skall rundt denne.
 */
@Component
public class TurvaerService {

    // Større rute = færre kandidater = færre vær-oppslag (raskere).
    private static final double CELL_DEGREES = 0.40;

    // Høydegulv for kandidat-utvelgelse. Fjelltur = bare høye topper; ellers
    // tar vi med alt (havniva-korreksjonen i scoringen hindrer at lave knauser
    // vinner på ren varme, HANDOFF §7).
    private static final double FJELL_MIN_ELEVATION_M = 800;
    private static final double DEFAULT_MIN_ELEVATION_M = 0;

    // Hvor langt fra hvert sted vi leter etter merkede turruter, og hvor mange
    // av topp-stedene vi henter turer rundt (i én union-spørring).
    private static final int TRAIL_RADIUS_M = 8000;
    private static final int TRAIL_PLACES = 10;

    // Når vi rangerer selve turrutene: tynn ut til ett spredt utvalg så vi ikke
    // gjør hundrevis av vær-oppslag.
    private static final double TRAIL_CELL_DEGREES = 0.05; // ~5 km rutenett
    private static final int MAX_TRAIL_CANDIDATES = 25;

    // Antall dager fram detaljsiden viser varsel for (MET dekker ~9-10 dager).
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final int FORECAST_DAYS = 9;

    private final QueryInterpreter interpreter;
    private final OverpassClient overpassClient;
    private final BestWeatherFinder bestWeatherFinder;
    private final ResilientWeatherClient weatherClient;
    private final OpenMeteoGeocodingClient geocoding;

    public TurvaerService(QueryInterpreter interpreter,
                          OverpassClient overpassClient,
                          BestWeatherFinder bestWeatherFinder,
                          ResilientWeatherClient weatherClient,
                          OpenMeteoGeocodingClient geocoding) {
        this.interpreter = interpreter;
        this.overpassClient = overpassClient;
        this.bestWeatherFinder = bestWeatherFinder;
        this.weatherClient = weatherClient;
        this.geocoding = geocoding;
    }

    /** Finn beste vær med normal vekt på alle faktorer. */
    public TurResult finnBesteVaer(String query) {
        return finnBesteVaer(query, ScoreWeights.DEFAULT);
    }

    public TurResult finnBesteVaer(String query, ScoreWeights weights) {
        Interpretation tolkning = interpreter.interpret(query);
        if (!tolkning.hasRegion()) {
            return new TurResult(tolkning, List.of(), List.of(), List.of());
        }

        // «Hvordan blir været i Oslo» er ikke et rangeringsspørsmål: slå opp
        // stedet (geocoding) og vis varselet for akkurat det, med turer og
        // klær-råd rundt - i stedet for å rangere topper i området.
        List<Location> candidates = switch (tolkning.target()) {
            case VARSEL -> geocoding.findFirst(tolkning.region()).map(List::of).orElse(List.of());
            case TUR -> trailCandidates(tolkning.region(), tolkning.country());
            case STED -> peakCandidates(tolkning.region(), tolkning.country(), tolkning.tripType());
        };

        List<RankedPlaceOverPeriod> ranking =
                bestWeatherFinder.rankOverPeriod(candidates, tolkning.dates().days(), weights);

        if (ranking.isEmpty()) {
            return new TurResult(tolkning, List.of(), List.of(), List.of());
        }

        // I TUR-modus ER de rangerte stedene allerede turruter; ellers viser vi
        // merkede turer nær topp-stedene (idé #1). Gjelder også VARSEL-stedet.
        List<Trail> trails = tolkning.target() == Target.TUR
                ? List.of()
                : overpassClient.trailsNear(topLocations(ranking), TRAIL_RADIUS_M);

        // Klær/utstyr-råd basert på været hos vinneren (idé #3).
        RankedPlaceOverPeriod winner = ranking.getFirst();
        List<String> clothing = ClothingAdvisor.recommend(
                winner.avgMaxTempC(), winner.avgPrecipMm(), winner.avgWindMs());

        return new TurResult(tolkning, ranking, trails, clothing);
    }

    /** Topper i området, redusert til et spredt kandidatsett. */
    private List<Location> peakCandidates(String region, String country, TripType tripType) {
        double minElevation = tripType == TripType.FJELLTUR
                ? FJELL_MIN_ELEVATION_M : DEFAULT_MIN_ELEVATION_M;
        List<Peak> peaks = overpassClient.peaksInArea(region, country);
        return CandidateSelector.representativePeaks(peaks, CELL_DEGREES, minElevation)
                .stream()
                .map(Peak::location)
                .toList();
    }

    /** Turruter i området (rute-senterpunkt), tynnet til et spredt kandidatsett. */
    private List<Location> trailCandidates(String region, String country) {
        List<Location> locations = overpassClient.trailsInArea(region, country).stream()
                .map(t -> new Location(t.name(), t.latitude(), t.longitude()))
                .toList();
        return thinByGrid(locations, TRAIL_CELL_DEGREES, MAX_TRAIL_CANDIDATES);
    }

    private List<Location> topLocations(List<RankedPlaceOverPeriod> ranking) {
        return ranking.stream()
                .limit(TRAIL_PLACES)
                .map(RankedPlaceOverPeriod::location)
                .toList();
    }

    /** Behold ett sted per rutenett-celle, så vær-oppslagene blir få og spredte. */
    private static List<Location> thinByGrid(List<Location> locations, double cellDegrees, int max) {
        Map<String, Location> perCell = new LinkedHashMap<>();
        for (Location l : locations) {
            long latIndex = Math.round(l.latitude() / cellDegrees);
            long lonIndex = Math.round(l.longitude() / cellDegrees);
            perCell.putIfAbsent(latIndex + ":" + lonIndex, l);
        }
        return perCell.values().stream().limit(max).toList();
    }

    /**
     * Detaljside for ett bestemt sted: flerdagers varsel, merkede turer i
     * nærheten og klær/utstyr-råd. Klær baseres på første dag med data (nærmest
     * i tid). Henter ett MET-varsel (dekker hele perioden) + ett Overpass-kall.
     */
    public PlaceForecast placeDetail(String name, double latitude, double longitude) {
        Location loc = new Location(name, latitude, longitude);
        Forecast forecast = weatherClient.fetch(loc);

        LocalDate today = LocalDate.now(OSLO);
        List<DayWeather> days = new ArrayList<>();
        for (int i = 0; i < FORECAST_DAYS; i++) {
            WeatherScorer.summarize(forecast, today.plusDays(i)).ifPresent(days::add);
        }

        List<Trail> trails = overpassClient.trailsNear(List.of(loc), TRAIL_RADIUS_M);

        List<String> clothing = days.isEmpty() ? List.of() : ClothingAdvisor.recommend(
                days.getFirst().maxTempC(), days.getFirst().totalPrecipMm(), days.getFirst().avgWindMs());

        return new PlaceForecast(name, forecast.elevationMeters(), days, trails, clothing);
    }
}
