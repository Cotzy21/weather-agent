package no.weatheragent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.weatheragent.training.ProgramProgress;
import no.weatheragent.training.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Innstillinger som følger kontoen på tvers av enheter: dashboard-oppsett, ukeserien (mål og pauser), og
 * treningsprogrammet (startdato + lengde) som gir «Dag 12/56».
 */
@RestController
public class SettingsController {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final UserSettingsService settings;

    public SettingsController(UserSettingsService settings) {
        this.settings = settings;
    }

    /** Dashboard-oppsettet og ukeserien (mål og pausede uker). 204 = ikke lagret ennå (frontenden bruker standard). */
    @GetMapping("/api/innstillinger/{key}")
    public ResponseEntity<JsonNode> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        if (!UserSettingsService.isClientKey(key)) return ResponseEntity.notFound().build();
        return settings.get(UUID.fromString(jwt.getSubject()), key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/api/innstillinger/{key}")
    public ResponseEntity<JsonNode> put(@AuthenticationPrincipal Jwt jwt, @PathVariable String key, @RequestBody JsonNode value) {
        if (!UserSettingsService.isClientKey(key)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(settings.put(UUID.fromString(jwt.getSubject()), key, value));
    }

    public record ProgramRequest(LocalDate startDate, int weeks) {
    }

    /** Programmet med fremdrift regnet ut i dag. 204 = ikke startet. */
    @GetMapping("/api/trening/program")
    public ResponseEntity<ProgramProgress> program(@AuthenticationPrincipal Jwt jwt) {
        return settings.get(UUID.fromString(jwt.getSubject()), UserSettingsService.PROGRAM)
                .map(v -> ResponseEntity.ok(ProgramProgress.of(
                        LocalDate.parse(v.path("startDate").asText()), v.path("weeks").asInt(), LocalDate.now(OSLO))))
                .orElse(ResponseEntity.noContent().build());
    }

    /** Start (eller endre) programmet: startdato og antall uker. */
    @PutMapping("/api/trening/program")
    public ProgramProgress startProgram(@AuthenticationPrincipal Jwt jwt, @RequestBody ProgramRequest request) {
        LocalDate today = LocalDate.now(OSLO);
        ProgramProgress progress = ProgramProgress.of(request.startDate(), request.weeks(), today); // validerer
        if (request.startDate().isAfter(today.plusYears(1))) {
            throw new IllegalArgumentException("Startdatoen er for langt frem i tid.");
        }
        ObjectNode value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        value.put("startDate", request.startDate().toString());
        value.put("weeks", request.weeks());
        settings.put(UUID.fromString(jwt.getSubject()), UserSettingsService.PROGRAM, value);
        return progress;
    }

    @DeleteMapping("/api/trening/program")
    public ResponseEntity<Void> endProgram(@AuthenticationPrincipal Jwt jwt) {
        settings.delete(UUID.fromString(jwt.getSubject()), UserSettingsService.PROGRAM);
        return ResponseEntity.noContent().build();
    }
}
