package no.weatheragent.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Forespørsel om å lagre en rute. Validert (security-first): navn må finnes og
 * være rimelig kort, og geometrien er begrenset i antall punkter.
 */
public record SaveRouteRequest(
        @NotBlank @Size(max = 120) String name,
        @PositiveOrZero double distanceKm,
        @PositiveOrZero double ascentM,
        @NotEmpty @Size(max = 2000) List<@Valid PointDto> geometry) {
}
