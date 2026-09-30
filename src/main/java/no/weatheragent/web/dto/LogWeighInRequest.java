package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Dato er valgfri (standard = i dag, norsk tid). */
public record LogWeighInRequest(LocalDate date, @NotNull Double weightKg) {
}
