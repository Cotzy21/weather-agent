package no.weatheragent.web.dto;

import no.weatheragent.training.AssistantReply;

import java.util.List;

/** Assistentens svar slik API-et eksponerer det: tekst, spørsmål og evt. plan. */
public record AssistantReplyDto(String reply, List<String> questions, PlanSuggestionDto plan) {

    public static AssistantReplyDto from(AssistantReply r) {
        return new AssistantReplyDto(
                r.reply(),
                r.questions(),
                r.plan() == null ? null : PlanSuggestionDto.from(r.plan()));
    }
}
