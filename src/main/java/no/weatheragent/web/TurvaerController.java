package no.weatheragent.web;

import no.weatheragent.advice.CalorieAdvisor;
import no.weatheragent.advice.RouteEstimate;
import no.weatheragent.assist.PlaceForecast;
import no.weatheragent.assist.TurResult;
import no.weatheragent.assist.TurvaerService;
import no.weatheragent.ranking.Impact;
import no.weatheragent.ranking.ScoreWeights;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-inngangen til tur-assistenten. Et tynt lag oppå {@link TurvaerService} -
 * all logikk ligger der. Nå kan et UI (eller curl) spørre i naturlig språk og
 * få svaret som JSON.
 *
 * De fire vekt-parametrene (temp/rain/wind/elevation) er Lav/Middels/Høy og
 * styrer hvor mye hver faktor påvirker rangeringen. De er valgfrie og defaulter
 * til Middels.
 *
 * Eksempel:
 *   GET /api/turvaer?q=finest vær i Rogaland neste uke&rain=hoy&elevation=lav
 */
@RestController
public class TurvaerController {

    private final TurvaerService service;

    public TurvaerController(TurvaerService service) {
        this.service = service;
    }

    @GetMapping("/api/turvaer")
    public TurResult turvaer(
            @RequestParam("q") String query,
            @RequestParam(value = "temp", defaultValue = "MIDDELS") String temp,
            @RequestParam(value = "rain", defaultValue = "MIDDELS") String rain,
            @RequestParam(value = "wind", defaultValue = "MIDDELS") String wind,
            @RequestParam(value = "elevation", defaultValue = "MIDDELS") String elevation) {

        ScoreWeights weights = ScoreWeights.of(
                Impact.fromString(temp),
                Impact.fromString(rain),
                Impact.fromString(wind),
                Impact.fromString(elevation));

        return service.finnBesteVaer(query, weights);
    }

    /**
     * Detaljside for ett sted: flerdagers varsel + turer + klær.
     * Eksempel: GET /api/sted?name=Slogen&lat=62.18&lon=6.86
     */
    @GetMapping("/api/sted")
    public PlaceForecast sted(
            @RequestParam("name") String name,
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon) {
        return service.placeDetail(name, lat, lon);
    }

    /**
     * Grovt estimat for en planlagt rute: tid, kaloriforbruk og mat/drikke.
     * Eksempel: GET /api/rute?distanceKm=12&ascentM=600&weightKg=80
     */
    @GetMapping("/api/rute")
    public RouteEstimate rute(
            @RequestParam("distanceKm") double distanceKm,
            @RequestParam(value = "ascentM", defaultValue = "0") double ascentM,
            @RequestParam(value = "weightKg", defaultValue = "75") double weightKg) {
        return CalorieAdvisor.estimate(distanceKm, ascentM, weightKg);
    }
}
