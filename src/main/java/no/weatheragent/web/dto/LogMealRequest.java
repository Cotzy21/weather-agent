package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Logg en matvare: hvilken dag, hvilket måltid, hvilken vare og hvor mange gram. */
public record LogMealRequest(
        @NotNull LocalDate date,
        @NotBlank String meal,
        @NotBlank String foodId,
        @Positive double grams
) {
}
