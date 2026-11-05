package no.weatheragent.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Samtalen så langt, sendt til treningsassistenten. Hver melding har en rolle
 * ("user"/"assistant") og innhold.
 */
public record AssistantRequest(@NotEmpty @Size(max = 60) List<@NotNull @Valid Message> messages, JsonNode currentPlan, @Size(max = 5) String lang) {

    public record Message(@NotBlank @Pattern(regexp = "user|assistant") String role, @NotNull @Size(max = 4000) String content) {
    }
}
