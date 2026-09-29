package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;

import no.weatheragent.route.RoutePoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    static final int MAX_POINTS_PER_LINE = 60;
    static final int MAX_LINES_PER_TRAIL = 8;
    static final int MAX_TRAILS = 25;

    /**
     * Svar fra {@code out geom}: ways har {@code geometry:[{lat,lon}…]}, relasjoner
     * har {@code members[].geometry}. Segmenter med samme navn slås sammen til én
     * sti (OSM deler ofte én sti i mange ways), linjene tynnes ut til maks
     * {@link #MAX_POINTS_PER_LINE} punkter, og senterpunktet er snittet av punktene.
     */
    public static List<Trail> parseWithGeometry(JsonNode root) {
        Map<String, List<List<RoutePoint>>> linesByName = new LinkedHashMap<>();
        Map<String, JsonNode> tagsByName = new LinkedHashMap<>();

        for (JsonNode element : root.path("elements")) {
            JsonNode tags = element.path("tags");
            String name = tags.path("name").asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            List<List<RoutePoint>> lines = new ArrayList<>();
            if ("relation".equals(element.path("type").asText())) {
                for (JsonNode member : element.path("members")) {
                    addLine(lines, member.path("geometry"));
                }
            } else {
                addLine(lines, element.path("geometry"));
            }
            if (lines.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            tagsByName.putIfAbsent(key, tags);
            List<List<RoutePoint>> existing = linesByName.computeIfAbsent(key, k -> new ArrayList<>());
            for (List<RoutePoint> line : lines) {
                if (existing.size() < MAX_LINES_PER_TRAIL) {
                    existing.add(line);
                }
            }
        }

        List<Trail> trails = new ArrayList<>();
        for (Map.Entry<String, List<List<RoutePoint>>> e : linesByName.entrySet()) {
            if (trails.size() >= MAX_TRAILS) {
                break;
            }
            JsonNode tags = tagsByName.get(e.getKey());
            double lat = 0;
            double lon = 0;
            int n = 0;
            for (List<RoutePoint> line : e.getValue()) {
                for (RoutePoint p : line) {
                    lat += p.lat();
                    lon += p.lon();
                    n++;
                }
            }
            trails.add(new Trail(tags.path("name").asText().trim(), tags.path("network").asText(""),
                    tags.path("operator").asText(""), lat / n, lon / n, List.copyOf(e.getValue())));
        }
        return trails;
    }

    /** Legg til én polylinje (minst 2 punkter), uttynnet til et håndterlig antall punkter. */
    private static void addLine(List<List<RoutePoint>> lines, JsonNode geometry) {
        List<RoutePoint> points = new ArrayList<>();
        for (JsonNode g : geometry) {
            // Utklipte geometrier (out geom(bbox)) har null-hull der lina forlater boksen.
            if (g.isNull() || !g.has("lat")) {
                continue;
            }
            points.add(new RoutePoint(g.path("lat").asDouble(), g.path("lon").asDouble()));
        }
        if (points.size() >= 2) {
            lines.add(thin(points, MAX_POINTS_PER_LINE));
        }
    }

    /** Behold hvert k-te punkt (første og siste alltid med), maks {@code max} punkter. */
    static List<RoutePoint> thin(List<RoutePoint> points, int max) {
        if (points.size() <= max) {
            return List.copyOf(points);
        }
        List<RoutePoint> out = new ArrayList<>(max);
        double step = (points.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) {
            out.add(points.get((int) Math.round(i * step)));
        }
        return List.copyOf(out);
    }
}
