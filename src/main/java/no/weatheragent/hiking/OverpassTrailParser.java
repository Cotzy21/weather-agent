package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Oversetter Overpass sitt JSON-svar for turruter til en liste {@link Trail}.
 * Ren og testbar - samme mønster som {@link OverpassPeakParser}.
 *
 * Vi spør med "out center", så hver relation har et senterpunkt:
 * <pre>
 * elements[] : { type:"relation", center:{ lat, lon },
 *                tags:{ route:"hiking", name, network, operator } }
 * </pre>
 * Vi hopper over ruter uten navn eller uten senterkoordinat.
 */
public final class OverpassTrailParser {

    private OverpassTrailParser() {
    }

    public static List<Trail> parse(JsonNode root) {
        List<Trail> trails = new ArrayList<>();

        for (JsonNode element : root.path("elements")) {
            JsonNode tags = element.path("tags");
            JsonNode center = element.path("center");

            String name = tags.path("name").asText("");
            if (name.isBlank() || center.isMissingNode()) {
                continue;
            }

            trails.add(new Trail(
                    name,
                    tags.path("network").asText(""),
                    tags.path("operator").asText(""),
                    center.path("lat").asDouble(),
                    center.path("lon").asDouble()));
        }

        return trails;
    }
}
