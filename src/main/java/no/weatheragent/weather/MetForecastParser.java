package no.weatheragent.weather;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Oversetter MET sitt raa JSON-svar til vaart eget {@link Forecast}.
 *
 * Holdt adskilt fra HTTP-klienten med vilje: da kan vi teste tolkningen
 * mot et fast JSON-eksempel uten aa ringe det ekte API-et.
 *
 * MET "compact"-svaret ser forenklet slik ut:
 * <pre>
 * geometry.coordinates : [lon, lat, altitude]
 * properties.timeseries[] :
 *   time
 *   data.instant.details.air_temperature
 *   data.instant.details.wind_speed
 *   data.next_1_hours.details.precipitation_amount
 * </pre>
 */
public final class MetForecastParser {

    private MetForecastParser() {
    }

    public static Forecast parse(Location location, JsonNode root) {
        // MET oppgir hoyden den la varselet til som tredje koordinat. Mangler den,
        // behandler vi stedet som havniva (0 m).
        double elevation = root.path("geometry").path("coordinates").path(2).asDouble(0.0);

        List<WeatherPoint> points = new ArrayList<>();

        for (JsonNode entry : root.path("properties").path("timeseries")) {
            Instant time = Instant.parse(entry.path("time").asText());

            JsonNode details = entry.path("data").path("instant").path("details");
            double temperature = details.path("air_temperature").asDouble();
            double wind = details.path("wind_speed").asDouble();

            // Nedbor finnes bare for tidspunkter som har en "neste time"-prognose.
            // For de siste punktene mangler den, og da regner vi det som 0.
            double precipitation = entry.path("data")
                    .path("next_1_hours").path("details")
                    .path("precipitation_amount").asDouble(0.0);

            points.add(new WeatherPoint(time, temperature, precipitation, wind));
        }

        return new Forecast(location, elevation, points);
    }
}
