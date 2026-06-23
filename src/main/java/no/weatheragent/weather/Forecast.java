package no.weatheragent.weather;

import no.weatheragent.geo.Location;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Hele vaervarselet for ett sted: en liste med tidspunkter framover.
 * MET gir typisk timesoppllosning de neste dagene.
 *
 * elevationMeters er hoyden MET la varselet til (fra geometry.coordinates[2]).
 * Vi bruker den til aa korrigere temperatur til havniva ved scoring, slik at
 * lave steder ikke alltid vinner paa ren varme (HANDOFF §7).
 */
public record Forecast(Location location, double elevationMeters, List<WeatherPoint> points) {

    /** Norsk tidssone - brukes naar vi skal tolke "fredag" som en lokal dato. */
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    /** Bakoverkompatibel: ukjent hoyde behandles som havniva (0 m). */
    public Forecast(Location location, List<WeatherPoint> points) {
        this(location, 0.0, points);
    }

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
