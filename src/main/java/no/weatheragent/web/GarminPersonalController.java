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

@RestController
public class GarminPersonalController {
    private static final Logger log = LoggerFactory.getLogger(GarminPersonalController.class);
    private final GarminPersonalClient garmin;

    public GarminPersonalController(GarminPersonalClient garmin) {
        this.garmin = garmin;
    }

    @PostMapping("/api/trening/garmin/aktiviteter")
    public ResponseEntity<?> activities(@RequestBody JsonNode body) {
        String supplied = body.path("apiKey").textValue();
        String key = supplied == null ? "" : supplied.strip();
        if (key.length() > 1024 || !key.matches("[!-~]+")) {
            return error(HttpStatus.BAD_REQUEST, "Ugyldig Garmin-nøkkel.");
        }

        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(garmin.recentActivities(key));
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
