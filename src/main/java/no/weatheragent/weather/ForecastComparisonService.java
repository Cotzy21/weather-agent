package no.weatheragent.weather;

import no.weatheragent.geo.Location;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Two independently fetched sources, never substituted for one another. */
@Service
public class ForecastComparisonService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ForecastComparisonService.class);
    private static final Map<String, Location> PLACES = Map.of(
            "alesund", new Location("Ålesund", 62.4722, 6.1495),
            "bergen", new Location("Bergen", 60.3913, 5.3221),
            "oslo", new Location("Oslo", 59.9139, 10.7522),
            "tromso", new Location("Tromsø", 69.6492, 18.9553));
    private final MetWeatherClient met;
    private final OpenMeteoWeatherClient openMeteo;

    public ForecastComparisonService(MetWeatherClient met, OpenMeteoWeatherClient openMeteo) {
        this.met = met;
        this.openMeteo = openMeteo;
    }

    public record Point(Instant time, double temperatureC, double windSpeedMs) {}
    public record Source(String name, List<Point> points, boolean available) {}
    public record Comparison(Location location, Instant fetchedAt, Source met, Source openMeteo) {}

    @Cacheable(value = "forecastComparison", sync = true)
    public Comparison compare(String place) {
        Location location = PLACES.get(place);
        if (location == null) throw new IllegalArgumentException("Velg et av stedene i listen.");
        Instant now = Instant.now();
        return new Comparison(location, now,
                source("MET Norway", () -> met.fetch(location), now),
                source("Open-Meteo", () -> openMeteo.fetch(location), now));
    }

    private Source source(String name, Supplier<Forecast> fetch, Instant now) {
        try {
            List<Point> points = fetch.get().points().stream()
                    .filter(p -> !p.time().isBefore(now) && p.time().isBefore(now.plus(25, ChronoUnit.HOURS)))
                    .filter(p -> Double.isFinite(p.temperatureC()) && Double.isFinite(p.windSpeedMs()))
                    .sorted(java.util.Comparator.comparing(WeatherPoint::time))
                    .limit(25)
                    .map(p -> new Point(p.time(), p.temperatureC(), p.windSpeedMs())).toList();
            if (points.isEmpty()) log.warn("Forecast comparison: {} returned no readings in the comparison window", name);
            return new Source(name, points, !points.isEmpty());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("Forecast comparison: {} returned HTTP {}", name, e.getStatusCode().value());
            return new Source(name, List.of(), false);
        } catch (RestClientException e) {
            log.warn("Forecast comparison: {} unavailable ({})", name, e.getClass().getSimpleName());
            return new Source(name, List.of(), false);
        }
    }
}
