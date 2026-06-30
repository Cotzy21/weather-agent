package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;

/** Et AI-generert økt-forslag: tittel, type, innhold (samme format vi lagrer), og begrunnelse. */
public record Suggestion(String title, String type, JsonNode content, String rationale) {
}
