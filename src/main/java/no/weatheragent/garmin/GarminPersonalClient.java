package no.weatheragent.garmin;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.hiking.ResponseSizeLimit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class GarminPersonalClient {
    static final String BASE_URL = "https://personal.connectapi.garmin.com";
    static final String TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token";
    static final String ACTIVITY_PATH = "/activitylist-service/activities/search/activities";
    private static final int LIMIT = 20;
    private final RestClient http;

    @Autowired
    public GarminPersonalClient(RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        factory.setReadTimeout(Duration.ofSeconds(12));
        http = builder.clone().baseUrl(BASE_URL).requestFactory(factory)
                .requestInterceptor(new ResponseSizeLimit(512_000)).build();
    }

    GarminPersonalClient(RestClient http) {
        this.http = http;
    }

    private String accessToken(String key) {
        String response = http.post().uri(TOKEN_PATH)
                .header(HttpHeaders.AUTHORIZATION, "ApiKey " + key)
                .accept(MediaType.ALL)
                .retrieve().body(String.class);
        String token = response == null ? "" : response.strip();
        if (token.length() > 8192 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw new RestClientException("Invalid Garmin token response");
        }

        return token;
    }

    public List<Activity> recentActivities(String key) {
        String token = accessToken(key);
        JsonNode data = http.get().uri(uri -> uri.path(ACTIVITY_PATH)
                        .queryParam("start", 0).queryParam("limit", LIMIT).build())
                .headers(headers -> headers.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class);
        if (data == null) throw new RestClientException("Missing Garmin activity response");
        JsonNode items = data.isArray() ? data : data.path("data");
        if (!items.isArray()) throw new RestClientException("Invalid Garmin activity response");

        List<Activity> result = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode id = item.path("activityId");
            if (!id.canConvertToLong() || !id.isIntegralNumber() || id.longValue() <= 0) continue;
            result.add(new Activity(id.asText(), field(item, "activityName", 150),
                    field(item, "startTimeLocal", 32), field(item.path("activityType"), "typeKey", 50),
                    metric(item.path("duration")), metric(item.path("distance")),
                    field(item, "deviceName", 100), metric(item.path("elevationGain")),
                    metric(item.path("calories")), whole(item.path("totalSets")), whole(item.path("totalReps")),
                    whole(item.path("averageHR")), whole(item.path("maxHR"))));
            if (result.size() == LIMIT) break;
        }
        if (result.isEmpty() && !items.isEmpty()) throw new RestClientException("Invalid Garmin activity records");
        return result;
    }

    public List<ActivityFile> activityFiles(String key, List<Long> ids) {
        String token = accessToken(key);
        List<ActivityFile> files = new ArrayList<>();
        for (long id : ids) {
            try {
                byte[] zip = http.get().uri("/download-service/files/activity/{id}", id)
                        .headers(headers -> headers.setBearerAuth(token))
                        .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.parseMediaType("application/zip"))
                        .retrieve().body(byte[].class);
                if (zip != null && zip.length > 0) files.add(new ActivityFile(Long.toString(id), zip));
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                // Some manually logged activities have no original FIT file.
            }
        }
        return files;
    }

    private static Integer whole(JsonNode value) {
        Double number = metric(value);
        return number == null || number > Integer.MAX_VALUE ? null : (int) Math.round(number);
    }

    private static String field(JsonNode item, String name, int maxLength) {
        String value = item.path(name).textValue();
        return value == null || value.length() > maxLength ? null : value;
    }

    private static Double metric(JsonNode value) {
        if (!value.isNumber()) return null;
        double number = value.doubleValue();
        return Double.isFinite(number) && number >= 0 ? number : null;
    }

    public record Activity(String id, String name, String startLocal, String type,
                           Double durationSeconds, Double distanceMeters, String deviceName,
                           Double ascentM, Double kcal, Integer totalSets, Integer totalReps, Integer avgHr, Integer maxHr) {}

    public record ActivityFile(String id, byte[] zip) {}
}
