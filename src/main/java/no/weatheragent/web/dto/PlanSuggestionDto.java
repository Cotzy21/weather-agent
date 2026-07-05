package no.weatheragent.web.dto;

import no.weatheragent.training.PlanSuggestion;

import java.util.List;

/** En AI-generert plan (én eller flere økter) slik API-et eksponerer den. */
public record PlanSuggestionDto(String title, String summary, List<SuggestionDto> workouts) {

    public static PlanSuggestionDto from(PlanSuggestion p) {
        return new PlanSuggestionDto(
                p.title(),
                p.summary(),
                p.workouts().stream().map(SuggestionDto::from).toList());
    }
}
