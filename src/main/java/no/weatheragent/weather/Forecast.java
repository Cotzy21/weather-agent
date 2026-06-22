package no.weatheragent.weather;

import no.weatheragent.geo.Location;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Hele vaervarselet for ett sted: en liste med tidspunkter framover.
 * MET gir typisk timesoppllosning de neste dagene.
 */
public record Forecast(Location location, List<WeatherPoint> points) {

    /** Norsk tidssone - brukes naar vi skal tolke "fredag" som en lokal dato. */
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    /**
     * Alle vaerpunkter som faller paa en gitt dato (norsk lokaltid).
     * Dette blir byggeklossen naar vi senere skal score "vaeret paa fredag".
     */
    public List<WeatherPoint> pointsOn(LocalDate date) {
        return points.stream()
                .filter(p -> p.time().atZone(OSLO).toLocalDate().equals(date))
                .toList();
    }
}
