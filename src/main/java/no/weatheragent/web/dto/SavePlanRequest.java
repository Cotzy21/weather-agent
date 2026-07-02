package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/** Lagre en treningsplan - feltene matcher et AI-forslag, så det kan sendes rett inn. */
public record SavePlanRequest(
        @NotBlank String title,
        @NotBlank String type,
        JsonNode content,
        String rationale
) {
}
