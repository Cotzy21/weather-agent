package no.weatheragent.weather;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Oversetter Open-Meteo sitt raa JSON-svar til vaart eget {@link Forecast}.
 * Samme rolle som {@link MetForecastParser}, bare for fallback-kilden.
 *
 * Open-Meteo-svaret (med timezone=UTC) ser forenklet slik ut:
 * <pre>
 * elevation : 900.0
 * hourly :
 *   time[]            : "2026-07-02T00:00" (UTC, uten offset)
 *   temperature_2m[]  : grader celsius
 *   precipitation[]   : mm siste time
 *   wind_speed_10m[]  : m/s (naar wind_speed_unit=ms er satt)
 * </pre>
 * Alle listene er like lange og hoerer sammen indeks for indeks.
 */
public final class OpenMeteoForecastParser {

    private OpenMeteoForecastParser() {
    }

    public static Forecast parse(Location location, JsonNode root) {
        double elevation = root.path("elevation").asDouble(0.0);

        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        JsonNode temperatures = hourly.path("temperature_2m");
        JsonNode precipitations = hourly.path("precipitation");
        JsonNode winds = hourly.path("wind_speed_10m");

        List<WeatherPoint> points = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            points.add(new WeatherPoint(
                    LocalDateTime.parse(times.get(i).asText()).toInstant(ZoneOffset.UTC),
                    temperatures.path(i).asDouble(),
                    precipitations.path(i).asDouble(),
                    winds.path(i).asDouble()));
        }

        return new Forecast(location, elevation, points);
    }
}
