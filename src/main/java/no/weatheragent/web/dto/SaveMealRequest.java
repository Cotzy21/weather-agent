package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Klientens ønske om å lagre et egendefinert måltid. */
public record SaveMealRequest(
        @NotBlank @Size(max = 120) String name,
        JsonNode ingredients) {
}
