package no.weatheragent.geo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Oversetter Open-Meteo sitt geocoding-JSON til en liste med {@link Location}.
 *
 * Vi filtrerer til norske treff, siden dette er en norsk turvaer-app og
 * stedsnavn som "Stranda" ellers kan matche steder i andre land.
 *
 * Svaret fra Open-Meteo ser forenklet slik ut:
 * <pre>
 * results[] : { name, latitude, longitude, country_code, admin1 }
 * </pre>
 */
public final class GeocodingResponseParser {

    private GeocodingResponseParser() {
    }

    public static List<Location> parse(JsonNode root) {
        List<Location> matches = new ArrayList<>();

        for (JsonNode result : root.path("results")) {
            String countryCode = result.path("country_code").asText("");
            if (!countryCode.isEmpty() && !countryCode.equals("NO")) {
                continue;
            }

            matches.add(new Location(
                    result.path("name").asText(),
                    result.path("latitude").asDouble(),
                    result.path("longitude").asDouble()
            ));
        }

        return matches;
    }
}
