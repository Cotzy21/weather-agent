package no.weatheragent.route;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ruter langs faktiske stier via OpenRouteService (foot-hiking-profil). Returnerer
 * traséen som følger stiene, samt lengde og stigning fra høydeprofilen.
 *
 * Krever en (gratis) API-nøkkel i {@code ors.api-key}. Er den tom, er klienten
 * deaktivert, og ruteplanleggeren faller tilbake til rett linje + Open-Meteo-høyde.
 * Nøkkelen settes via miljøvariabel (ORS_API_KEY), aldri i git.
 */
@Component
public class RoutingClient {

    private final RestClient http;
    private final String apiKey;

    public RoutingClient(RestClient.Builder builder,
                         @Value("${ors.base-url:https://api.openrouteservice.org}") String baseUrl,
                         @Value("${ors.api-key:}") String apiKey) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public RoutedPath route(List<RoutePoint> waypoints) {
        // ORS vil ha [lon, lat]-par, og kan gi høyde (z) når elevation=true.
        List<List<Double>> coordinates = waypoints.stream()
                .map(p -> List.of(p.lon(), p.lat()))
                .toList();
        Map<String, Object> body = Map.of("coordinates", coordinates, "elevation", true);

        JsonNode root = http.post()
                .uri("/v2/directions/foot-hiking/geojson")
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode feature = root.path("features").path(0);
        JsonNode properties = feature.path("properties");

        double distanceKm = properties.path("summary").path("distance").asDouble() / 1000.0;

        JsonNode coords = feature.path("geometry").path("coordinates");
        List<RoutePoint> geometry = new ArrayList<>(coords.size());
        double[] elevations = new double[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            JsonNode c = coords.get(i);
            geometry.add(new RoutePoint(c.get(1).asDouble(), c.get(0).asDouble()));
            elevations[i] = c.size() > 2 ? c.get(2).asDouble() : 0;
        }

        double ascent = properties.has("ascent")
                ? properties.path("ascent").asDouble()
                : RouteGeometry.ascentM(elevations);

        return new RoutedPath(distanceKm, ascent, geometry);
    }
}
