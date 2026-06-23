package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Oversetter Overpass (OpenStreetMap) sitt JSON-svar til en liste {@link Peak}.
 *
 * Holdt adskilt fra HTTP-klienten med vilje, så vi kan teste tolkningen mot
 * et fast JSON-eksempel uten å spørre Overpass.
 *
 * Svaret har formen:
 * <pre>
 * elements[] : { type:"node", lat, lon, tags: { natural:"peak", name, ele } }
 * </pre>
 * Vi hopper over noder uten navn eller uten koordinat.
 */
public final class OverpassPeakParser {

    private OverpassPeakParser() {
    }

    public static List<Peak> parse(JsonNode root) {
        List<Peak> peaks = new ArrayList<>();

        for (JsonNode element : root.path("elements")) {
            JsonNode tags = element.path("tags");

            String name = tags.path("name").asText();
            if (name.isBlank() || !element.has("lat") || !element.has("lon")) {
                continue;
            }

            Location location = new Location(name, element.path("lat").asDouble(), element.path("lon").asDouble());
            Double elevation = tags.has("ele") ? parseElevation(tags.get("ele").asText()) : null;

            peaks.add(new Peak(location, elevation));
        }

        return peaks;
    }

    /**
     * OSM sin "ele"-tag er som regel et tall ("1564"), men kan ha enhet ("1564 m")
     * eller komma som desimaltegn. Vi plukker ut det første tallet, eller null.
     */
    private static Double parseElevation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String first = raw.trim().split("\\s+")[0].replace(",", ".");
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
