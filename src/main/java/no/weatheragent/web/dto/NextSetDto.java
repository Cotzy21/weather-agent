package no.weatheragent.web.dto;

import no.weatheragent.training.NextSetSuggestion;

import java.time.LocalDate;
import java.util.List;

/** Et progressive overload-forslag for én øvelse, slik /api/ovelser/neste leverer det. */
public record NextSetDto(
        String exercise,
        LocalDate lastDate,
        double lastWeightKg,
        List<Integer> lastReps,
        String action,
        double nextWeightKg,
        int nextReps,
        int sets,
        String reason
) {

    public static NextSetDto from(NextSetSuggestion s) {
        return new NextSetDto(s.exercise(), s.lastDate(), s.lastWeightKg(), s.lastReps(),
                s.action().name(), s.nextWeightKg(), s.nextReps(), s.sets(), s.reason());
    }
}
