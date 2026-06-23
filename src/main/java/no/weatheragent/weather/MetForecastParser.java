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

            double precipitation = precipitationAmount(entry.path("data"));

            points.add(new WeatherPoint(time, temperature, precipitation, wind));
        }

        return new Forecast(location, elevation, points);
    }

    /**
     * Nedbor for tidspunktet. MET legger nedbor i ulike vinduer avhengig av hvor
     * langt fram varselet er: de forste ~2 dognene i {@code next_1_hours}, lenger
     * fram bare i {@code next_6_hours} (og helt ut i {@code next_12_hours}). Vi tar
     * det fineste tilgjengelige vinduet, slik at nedbor ikke feilaktig blir 0 for
     * dager lenger fram. Vinduene overlapper ikke naar man summerer punkt for punkt:
     * i timesomraadet har hvert punkt next_1_hours, i 6-timersomraadet next_6_hours.
     */
    private static double precipitationAmount(JsonNode data) {
        for (String window : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
            JsonNode amount = data.path(window).path("details").path("precipitation_amount");
            if (!amount.isMissingNode()) {
                return amount.asDouble(0.0);
            }
        }
        return 0.0; // helt ute i varslet kan nedbor mangle helt
    }
}
