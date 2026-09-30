package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.body.WeighInService;
import no.weatheragent.web.dto.LogWeighInRequest;
import no.weatheragent.web.dto.WeighInDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Kroppsvekt: innveiinger for trendgraf og år-rutenett. */
@RestController
public class BodyweightController {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final WeighInService weighIns;

    public BodyweightController(WeighInService weighIns) {
        this.weighIns = weighIns;
    }

    @GetMapping("/api/kropp/vekt")
    public List<WeighInDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return weighIns.listFor(UUID.fromString(jwt.getSubject())).stream().map(WeighInDto::from).toList();
    }

    @PostMapping("/api/kropp/vekt")
    public WeighInDto log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogWeighInRequest request) {
        LocalDate today = LocalDate.now(OSLO);
        LocalDate date = request.date() == null ? today : request.date();
        return WeighInDto.from(weighIns.log(UUID.fromString(jwt.getSubject()), date, request.weightKg(), today));
    }

    @DeleteMapping("/api/kropp/vekt/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = weighIns.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
