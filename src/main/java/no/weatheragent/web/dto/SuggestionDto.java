package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.training.Suggestion;

/** Et AI-økt-forslag slik API-et eksponerer det (samme content-format som en lagret økt). */
public record SuggestionDto(String title, String type, JsonNode content, String rationale) {

    public static SuggestionDto from(Suggestion s) {
        return new SuggestionDto(s.title(), s.type(), s.content(), s.rationale());
    }
}
