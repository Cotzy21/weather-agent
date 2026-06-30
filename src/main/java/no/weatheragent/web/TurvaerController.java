package no.weatheragent.web;

import no.weatheragent.advice.RouteEstimate;
import no.weatheragent.assist.PlaceForecast;
import no.weatheragent.assist.TurResult;
import no.weatheragent.assist.TurvaerService;
import no.weatheragent.ranking.Impact;
import no.weatheragent.ranking.ScoreWeights;
import no.weatheragent.route.RoutePlannerService;
import no.weatheragent.route.RouteRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final RoutePlannerService routePlanner;

    public TurvaerController(TurvaerService service, RoutePlannerService routePlanner) {
        this.service = service;
        this.routePlanner = routePlanner;
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
     * Estimat for en planlagt rute fra klikkede waypoints: lengde, automatisk
     * stigning (høydeprofil), tid, kaloriforbruk og mat/drikke.
     * Body: { "waypoints": [{"lat":..,"lon":..}, ...], "weightKg": 80 }
     */
    @PostMapping("/api/rute")
    public RouteEstimate rute(@RequestBody RouteRequest request) {
        return routePlanner.plan(request);
    }
}
