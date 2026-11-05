package no.weatheragent.web;

import no.weatheragent.garmin.GarminInsightsService;
import no.weatheragent.garmin.GarminWellnessReader;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/recovery/garmin")
public class GarminInsightsController {
    private final GarminWellnessReader reader;
    private final GarminInsightsService store;

    public GarminInsightsController(GarminWellnessReader reader, GarminInsightsService store) {
        this.reader = reader;
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<List<GarminInsightsService.Day>> days(@AuthenticationPrincipal Jwt jwt,
                                                                @RequestParam(defaultValue = "30") int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("Velg mellom 1 og 90 dager.");
        LocalDate today = today();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(store.list(user(jwt), today.minusDays(days - 1L), today));
    }

    @PostMapping
    public ResponseEntity<?> sync(@AuthenticationPrincipal Jwt jwt, @RequestBody SyncRequest request) {
        String key = request.apiKey() == null ? "" : request.apiKey().strip();
        if (key.length() > 1024 || !key.matches("[!-~]+")) throw new IllegalArgumentException("Ugyldig Garmin-nøkkel.");
        LocalDate today = today();
        LocalDate from = request.from();
        LocalDate to = request.to();
        var group = request.group();
        int maxDays = group == GarminWellnessReader.Group.FOOD ? 7 : group == GarminWellnessReader.Group.DETAIL ? 1 : 28;
        if (group == null || from == null || to == null || from.isAfter(to) || to.isAfter(today)
                || from.isBefore(today.minusYears(1))
                || ChronoUnit.DAYS.between(from, to) >= maxDays) {
            throw new IllegalArgumentException("Velg en gyldig periode for datasettet.");
        }
        try {
            int saved = store.save(user(jwt), reader.read(key, group, from, to));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("days", saved));
        } catch (RestClientResponseException e) {
            return switch (e.getStatusCode().value()) {
                case 401, 403 -> error(422, "Garmin avviste nøkkelen. Sjekk at den er gyldig og ikke har utløpt.");
                case 429 -> error(429, "Garmin ber deg vente litt før du prøver igjen.");
                default -> error(502, "Kunne ikke hente Garmin-data. Prøv igjen om litt.");
            };
        } catch (RestClientException e) {
            return error(502, "Kunne ikke hente Garmin-data. Prøv igjen om litt.");
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        store.delete(user(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Oslo"));
    }

    private static UUID user(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static ResponseEntity<ApiExceptionHandler.ApiError> error(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiExceptionHandler.ApiError(message));
    }

    public record SyncRequest(String apiKey, GarminWellnessReader.Group group, LocalDate from, LocalDate to) {
        @Override public String toString() { return "GarminSyncRequest"; }
    }
}
