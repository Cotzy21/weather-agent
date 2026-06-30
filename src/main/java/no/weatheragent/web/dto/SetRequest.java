package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Ett sett i en logget økt (validert input). */
public record SetRequest(
        @NotBlank @Size(max = 120) String exercise,
        @Positive int reps,
        @PositiveOrZero double weightKg) {
}
