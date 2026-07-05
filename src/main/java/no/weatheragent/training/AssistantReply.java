package no.weatheragent.training;

import java.util.List;

/**
 * Assistentens svar på én tur: enten oppfølgingsspørsmål ELLER en ferdig plan
 * (eller begge felt tomme/null bortsett fra en kort {@code reply}).
 *
 * @param reply     kort tekst-svar til brukeren
 * @param questions oppfølgingsspørsmål (tomt når en plan er levert)
 * @param plan      den ferdige planen, eller {@code null} når assistenten spør
 */
public record AssistantReply(String reply, List<String> questions, PlanSuggestion plan) {
}
