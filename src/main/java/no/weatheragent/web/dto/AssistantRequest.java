package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Samtalen så langt, sendt til treningsassistenten. Hver melding har en rolle
 * ("user"/"assistant") og innhold.
 */
public record AssistantRequest(@NotEmpty List<Message> messages) {

    public record Message(String role, String content) {
    }
}
