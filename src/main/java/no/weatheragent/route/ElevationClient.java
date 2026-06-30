package no.weatheragent.route;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Henter terrenghøyde for en liste punkter fra Open-Meteo sitt høyde-API (gratis,
 * ingen nøkkel, opptil 100 punkter per kall). Samme leverandør som geocodingen vår.
 *
 * Svar: { "elevation": [470.0, 502.0, ...] } - i samme rekkefølge som punktene.
 */
@Component
public class ElevationClient {

    private final RestClient http;

    public ElevationClient(RestClient.Builder builder) {
        this.http = builder.baseUrl("https://api.open-meteo.com/v1").build();
    }

    public double[] elevations(List<RoutePoint> points) {
        if (points.isEmpty()) {
            return new double[0];
        }

        String lats = points.stream().map(p -> coord(p.lat())).collect(Collectors.joining(","));
        String lons = points.stream().map(p -> coord(p.lon())).collect(Collectors.joining(","));

        JsonNode root = http.get()
                .uri(uri -> uri.path("/elevation")
                        .queryParam("latitude", lats)
                        .queryParam("longitude", lons)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode arr = root.path("elevation");
        double[] out = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).asDouble();
        }
        return out;
    }

    private static String coord(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }
}
