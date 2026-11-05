package no.weatheragent.web;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.garmin.GarminPersonalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Supplier;

@RestController
public class GarminPersonalController {
    private static final Logger log = LoggerFactory.getLogger(GarminPersonalController.class);
    private final GarminPersonalClient garmin;

    public GarminPersonalController(GarminPersonalClient garmin) {
        this.garmin = garmin;
    }

    @PostMapping("/api/trening/garmin/aktiviteter")
    public ResponseEntity<?> activities(@RequestBody JsonNode body) {
        String key = key(body);
        return key == null ? error(HttpStatus.BAD_REQUEST, "Ugyldig Garmin-nøkkel.")
                : fetch(() -> garmin.recentActivities(key));
    }

    @PostMapping("/api/trening/garmin/filer")
    public ResponseEntity<?> files(@RequestBody JsonNode body) {
        String key = key(body);
        if (key == null) return error(HttpStatus.BAD_REQUEST, "Ugyldig Garmin-nøkkel.");
        JsonNode ids = body.path("ids");
        if (!ids.isArray() || ids.isEmpty() || ids.size() > 5) {
            return error(HttpStatus.BAD_REQUEST, "Velg mellom 1 og 5 Garmin-økter.");
        }
        List<Long> selected = new java.util.ArrayList<>();
        for (JsonNode id : ids) {
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
                return error(HttpStatus.BAD_REQUEST, "Ugyldig Garmin-aktivitet.");
            }
            selected.add(id.longValue());
        }
        return fetch(() -> garmin.activityFiles(key, selected.stream().distinct().toList()));
    }

    private static String key(JsonNode body) {
        String supplied = body.path("apiKey").textValue();
        String key = supplied == null ? "" : supplied.strip();
        return key.length() <= 1024 && key.matches("[!-~]+") ? key : null;
    }

    private static ResponseEntity<?> fetch(Supplier<?> call) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(call.get());
        } catch (RestClientResponseException e) {
            // Never relay Garmin's response body or log errors containing credentials or activity data.
            return switch (e.getStatusCode().value()) {
                case 400, 401, 403 -> error(HttpStatus.UNPROCESSABLE_ENTITY, "Garmin avviste nøkkelen. Sjekk at den er gyldig og ikke har utløpt.");
                case 429 -> error(HttpStatus.TOO_MANY_REQUESTS, "Garmin ber deg vente litt før du prøver igjen.");
                default -> {
                    log.warn("Garmin Personal API returned HTTP {}", e.getStatusCode().value());
                    yield unavailable();
                }
            };
        } catch (RestClientException e) {
            Throwable cause = e.getMostSpecificCause();
            log.warn("Garmin Personal API request failed ({})", cause.getClass().getSimpleName());
            return unavailable();
        }
    }

    private static ResponseEntity<ApiExceptionHandler.ApiError> unavailable() {
        return error(HttpStatus.BAD_GATEWAY, "Kunne ikke hente aktiviteter fra Garmin. Prøv igjen om litt.");
    }

    private static ResponseEntity<ApiExceptionHandler.ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new ApiExceptionHandler.ApiError(message));
    }
}
