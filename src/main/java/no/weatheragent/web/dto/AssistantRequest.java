package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Samtalen så langt, sendt til treningsassistenten. Hver melding har en rolle
 * ("user"/"assistant") og innhold.
 */
public record AssistantRequest(@NotEmpty @Size(max = 60) List<Message> messages, JsonNode currentPlan, @Size(max = 5) String lang) {

    public record Message(@Size(max = 20) String role, @Size(max = 4000) String content) {
    }
}
