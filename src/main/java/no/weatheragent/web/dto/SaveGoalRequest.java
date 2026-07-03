package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Lagre kalorimål-profilen; grensene sjekkes grundigere i tjenesten. */
public record SaveGoalRequest(
        @Positive double weightKg,
        @Positive double heightCm,
        @Positive int age,
        @NotBlank String sex,
        @NotBlank String activityLevel,
        double goalKgPerWeek
) {
}
