package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Forespørsel om et AI-treningsforslag: fritekst-fokus, og evt. ønsket type. */
public record SuggestionRequest(
        @NotBlank @Size(max = 200) String focus,
        @Size(max = 40) String type) {
}
