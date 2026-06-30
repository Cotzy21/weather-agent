package no.weatheragent.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Forespørsel om å logge en treningsøkt (validert). */
public record LogWorkoutRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 120) String title,
        @NotEmpty @Size(max = 100) List<@Valid SetRequest> sets) {
}
