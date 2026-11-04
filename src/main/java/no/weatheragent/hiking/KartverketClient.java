package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.geo.Location;
import no.weatheragent.route.RoutePoint;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Norwegian administrative regions, summits and marked walking paths from Kartverket.
 * Unknown regions and foreign countries explicitly remain with the global provider.
 * SSR points are sampled geographically before elevation lookup to bound network and heap use.
 * Sources: https://ws.geonorge.no/stedsnavn/v1/openapi.json and Kartverket's public WFS.
 */
@Component
public class KartverketClient {
    private final RestClient http;
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();
    private static final String ROOT = "https://ws.geonorge.no";
    private record Region(String kind, String number) {}

    @org.springframework.beans.factory.annotation.Autowired
    public KartverketClient(RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = builder.clone().requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    Long limit = deadline.get();
                    if (limit != null && System.nanoTime() >= limit) throw new java.io.IOException("Kartverkets søkebudsjett er brukt opp");
                    return execution.execute(request, body);
                })
                .requestInterceptor(new ResponseSizeLimit(2_000_000)).build();
    }

    KartverketClient(RestClient http) { this.http = http; }

    private JsonNode json(String path) {
        return http.get().uri(ROOT + path).retrieve().body(JsonNode.class);
    }
    private Optional<Region> region(String name, String country) {
        if (!"NO".equalsIgnoreCase(country)) return Optional.empty();
        String wanted = normalize(name);
        for (String kind : List.of("fylker", "kommuner")) {
            JsonNode regions = json("/kommuneinfo/v1/" + kind);
            if (regions == null || !regions.isArray()) throw new IllegalStateException("Ugyldig områdesvar fra Kartverket");
            String prefix = kind.equals("fylker") ? "fylkes" : "kommune";
            for (JsonNode r : regions) {
                if (normalize(r.path(prefix + "navn").asText()).equals(wanted)) {
                    return Optional.of(new Region(kind, r.path(prefix + "nummer").asText()));
                }
            }
        }
        return Optional.empty();
    }
    private static String normalize(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    @Cacheable(value = "kartverketPeaks", key = "#name.toLowerCase() + ':' + #country", unless = "#result == null")
    public Optional<List<Peak>> peaksInArea(String name, String country) {
        deadline.set(System.nanoTime() + Duration.ofSeconds(30).toNanos());
        try { return peaksInAreaLookup(name, country); }
        catch (org.springframework.web.client.RestClientException | IllegalStateException e) {
            throw new KartverketUnavailableException(e);
        } finally { deadline.remove(); }
    }
    private Optional<List<Peak>> peaksInAreaLookup(String name, String country) {
        Optional<Region> region = region(name, country);
        if (region.isEmpty()) return Optional.empty();
        Region r = region.get();
        Map<String, List<Location>> cells = new LinkedHashMap<>();
        // Four pages / 2,000 named summits maximum; five samples per 0.4-degree cell.
        for (int page = 1; page <= 4; page++) {
            final int current = page;
            JsonNode data = http.get().uri(b -> b.scheme("https").host("ws.geonorge.no").path("/stedsnavn/v1/sted")
                    .queryParam(r.kind.equals("fylker") ? "fnr" : "knr", r.number)
                    .queryParam("navneobjekttype", "topp").queryParam("utkoordsys", 4326)
                    .queryParam("treffPerSide", 500).queryParam("side", current).build()).retrieve().body(JsonNode.class);
            for (Location location : parsePlaces(data)) {
                String key = Math.round(location.latitude() / .4) + ":" + Math.round(location.longitude() / .4);
                List<Location> cell = cells.computeIfAbsent(key, ignored -> new ArrayList<>());
                if (cell.size() < 5) cell.add(location);
            }
            if (data.path("metadata").path("totaltAntallTreff").asInt() <= page * 500) break;
        }
        List<Location> locations = cells.values().stream().flatMap(List::stream).limit(200).toList();
        List<Peak> result = new ArrayList<>();
        for (int start = 0; start < locations.size(); start += 50) {
            List<Location> batch = locations.subList(start, Math.min(start + 50, locations.size()));
            String points = "[" + String.join(",", batch.stream().map(l -> "[" + l.longitude() + "," + l.latitude() + "]").toList()) + "]";
            JsonNode heights = http.get().uri(b -> b.scheme("https").host("ws.geonorge.no").path("/hoydedata/v1/punkt")
                    .queryParam("koordsys", 4326).queryParam("punkter", points).build()).retrieve().body(JsonNode.class);
            result.addAll(withElevations(batch, heights));
        }
        return Optional.of(List.copyOf(result));
    }

    static List<Location> parsePlaces(JsonNode root) {
        if (root == null || !root.path("navn").isArray()) throw new IllegalStateException("Ugyldig stedsnavnsvar");
        Map<Integer, Location> places = new LinkedHashMap<>();
        for (JsonNode place : root.path("navn")) {
            JsonNode point = place.path("representasjonspunkt");
            String name = "";
            for (JsonNode n : place.path("stedsnavn")) {
                if (name.isEmpty() || n.path("skrivemåtestatus").asText().contains("prioritert")) name = n.path("skrivemåte").asText();
            }
            if (!point.path("nord").isNumber() || !point.path("øst").isNumber() || name.isBlank()) continue;
            double lat = point.path("nord").asDouble(), lon = point.path("øst").asDouble();
            if (lat < 57 || lat > 72 || lon < 3 || lon > 34) continue;
            places.putIfAbsent(place.path("stedsnummer").asInt(), new Location(name, lat, lon));
        }
        return List.copyOf(places.values());
    }

    static List<Peak> withElevations(List<Location> locations, JsonNode root) {
        if (root == null || !root.path("punkter").isArray()) throw new IllegalStateException("Ugyldig høydesvar");
        List<Peak> result = new ArrayList<>();
        // Match by coordinates rather than assuming that the remote API preserves order.
        for (Location l : locations) {
            for (JsonNode p : root.path("punkter")) {
                if (!p.path("x").isNumber() || !p.path("y").isNumber() || !p.path("z").isNumber()) continue;
                double height = p.path("z").asDouble();
                if (Math.abs(p.path("x").asDouble() - l.longitude()) < .000001
                        && Math.abs(p.path("y").asDouble() - l.latitude()) < .000001
                        && Double.isFinite(height) && height >= 0 && height <= 3000) {
                    result.add(new Peak(l, height));
                    break;
                }
            }
        }
        return result;
    }

    @Cacheable(value = "kartverketAreaTrails", key = "#name.toLowerCase() + ':' + #country")
    public Optional<List<Trail>> trailsInArea(String name, String country) {
        deadline.set(System.nanoTime() + Duration.ofSeconds(30).toNanos());
        try { return trailsInAreaLookup(name, country); }
        catch (org.springframework.web.client.RestClientException | IllegalStateException e) {
            throw new KartverketUnavailableException(e);
        } finally { deadline.remove(); }
    }
    private Optional<List<Trail>> trailsInAreaLookup(String name, String country) {
        Optional<Region> region = region(name, country);
        if (region.isEmpty()) return Optional.empty();
        Region r = region.get();
        JsonNode area = json("/kommuneinfo/v1/" + r.kind + "/" + r.number + "?utkoordsys=4326");
        JsonNode geometry = json("/kommuneinfo/v1/" + r.kind + "/" + r.number + "/omrade?utkoordsys=4326").path("omrade");
        JsonNode ring = area.path("avgrensningsboks").path("coordinates").path(0);
        if (ring.size() < 4 || !geometry.has("coordinates")) throw new IllegalStateException("Ugyldig områdegeometri");
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (JsonNode p : ring) {
            minLon = Math.min(minLon, p.path(0).asDouble()); maxLon = Math.max(maxLon, p.path(0).asDouble());
            minLat = Math.min(minLat, p.path(1).asDouble()); maxLat = Math.max(maxLat, p.path(1).asDouble());
        }
        // Bounding-box query is narrowed by the exact administrative polygon below.
        List<List<Trail>> quadrants = new ArrayList<>();
        double midLat = (minLat + maxLat) / 2, midLon = (minLon + maxLon) / 2;
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                quadrants.add(fetchTrails(y == 0 ? minLat : midLat, x == 0 ? minLon : midLon,
                        y == 0 ? midLat : maxLat, x == 0 ? midLon : maxLon, 25).stream()
                        .filter(t -> KartverketTrailParser.inside(geometry, t.longitude(), t.latitude())).toList());
            }
        }
        // A single large bbox can return only neighboring-county features before the
        // limit is reached. Interleave four smaller, exactly filtered samples instead.
        Map<String, Trail> spread = new LinkedHashMap<>();
        for (int i = 0; i < 25; i++) {
            for (List<Trail> sample : quadrants) {
                if (i < sample.size()) {
                    Trail trail = sample.get(i);
                    spread.putIfAbsent(trail.name() + ":" + trail.latitude() + ":" + trail.longitude(), trail);
                }
            }
        }
        return Optional.of(spread.values().stream().limit(25).toList());
    }

    public boolean covers(Location point) {
        return point.latitude() >= 57 && point.latitude() <= 72 && point.longitude() >= 3 && point.longitude() <= 34;
    }

    @Cacheable(value = "kartverketNearTrails", key = "#center.latitude() + ',' + #center.longitude() + ',' + #radiusMeters")
    public List<Trail> trailsNear(Location center, int radiusMeters) {
        deadline.set(System.nanoTime() + Duration.ofSeconds(30).toNanos());
        try { return trailsNearLookup(center, radiusMeters); }
        catch (org.springframework.web.client.RestClientException | IllegalStateException e) {
            throw new KartverketUnavailableException(e);
        } finally { deadline.remove(); }
    }
    private List<Trail> trailsNearLookup(Location center, int radiusMeters) {
        double latDelta = radiusMeters / 111_000.0;
        double lonDelta = latDelta / Math.cos(Math.toRadians(center.latitude()));
        return fetchTrails(center.latitude() - latDelta, center.longitude() - lonDelta,
                center.latitude() + latDelta, center.longitude() + lonDelta, 25).stream()
                .filter(t -> t.lines().stream().flatMap(List::stream).anyMatch(p -> distance(center, p) <= radiusMeters))
                .limit(25).toList();
    }

    private List<Trail> fetchTrails(double minLat, double minLon, double maxLat, double maxLon, int count) {
        String bbox = minLat + "," + minLon + "," + maxLat + "," + maxLon + ",urn:ogc:def:crs:EPSG::4326";
        return http.get().uri(b -> b.scheme("https").host("wfs.geonorge.no").path("/skwms1/wfs.turogfriluftsruter")
                .queryParam("service", "WFS").queryParam("request", "GetFeature").queryParam("version", "2.0.0")
                .queryParam("typeNames", "app:Fotrute").queryParam("srsName", "urn:ogc:def:crs:EPSG::4326")
                .queryParam("bbox", bbox).queryParam("count", count).build()).exchange((request, response) -> {
                    if (response.getStatusCode().isError()) throw new IllegalStateException("Turrutekilden svarte HTTP " + response.getStatusCode().value());
                    // WFS sends an unquoted subtype=gml/3.2.1 parameter. Spring rejects that
                    // Content-Type during message conversion; read the bounded UTF-8 XML directly.
                    return KartverketTrailParser.parse(new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                });
    }
    private static double distance(Location c, RoutePoint p) {
        double a = Math.pow(Math.sin(Math.toRadians(p.lat() - c.latitude()) / 2), 2)
                + Math.cos(Math.toRadians(c.latitude())) * Math.cos(Math.toRadians(p.lat()))
                * Math.pow(Math.sin(Math.toRadians(p.lon() - c.longitude()) / 2), 2);
        return 6_371_000 * 2 * Math.asin(Math.sqrt(a));
    }
}
