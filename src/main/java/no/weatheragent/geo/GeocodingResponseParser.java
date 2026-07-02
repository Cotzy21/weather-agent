package no.weatheragent.geo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Oversetter Open-Meteo sitt geocoding-JSON til en liste med {@link Location}.
 *
 * Alle treff beholdes uansett land - appen dekker hele verden (vær fra MET
 * LocationForecast er globalt). Treffene kommer relevans-sortert fra Open-Meteo,
 * så tvetydige navn ("Stranda" finnes både i Norge og Italia) løses av
 * rekkefølgen + at kalleren kan vise flere kandidater.
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
            matches.add(new Location(
                    result.path("name").asText(),
                    result.path("latitude").asDouble(),
                    result.path("longitude").asDouble()
            ));
        }

        return matches;
    }
}
