package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/** Lagre en treningsplan - feltene matcher et AI-forslag, så det kan sendes rett inn. */
public record SavePlanRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 120) String title,
        @NotBlank @jakarta.validation.constraints.Size(max = 40) String type,
        JsonNode content,
        @jakarta.validation.constraints.Size(max = 4000) String rationale
) {
}
