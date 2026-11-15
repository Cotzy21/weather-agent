package no.weatheragent.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.weatheragent.account.HealthStorageConsent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/konto/helselagring")
public class HealthStorageConsentController {
    private final HealthStorageConsent consent;

    public HealthStorageConsentController(HealthStorageConsent consent) {
        this.consent = consent;
    }

    public record Grant(@NotNull Integer version, @NotNull Boolean allow) {}

    @GetMapping
    public HealthStorageConsent.Decision get(@AuthenticationPrincipal Jwt jwt) {
        return consent.get(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    public HealthStorageConsent.Decision grant(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Grant grant) {
        if (!Boolean.TRUE.equals(grant.allow())) throw new IllegalArgumentException("Samtykke må gis aktivt.");
        return consent.grant(UUID.fromString(jwt.getSubject()), grant.version());
    }

    @DeleteMapping
    public HealthStorageConsent.Decision withdraw(@AuthenticationPrincipal Jwt jwt) {
        return consent.withdrawAndErase(UUID.fromString(jwt.getSubject()));
    }
}
