package no.weatheragent.web.dto;

import no.weatheragent.training.ExerciseCatalog;
import no.weatheragent.training.ExerciseMatchService;

import java.util.List;

/** For hvert navn: appens øvelse (id + norsk/engelsk navn), eller null-felter når ingen øvelse er den samme. */
public record MatchExercisesResponse(List<Item> matches) {

    public record Item(String name, String id, String nb, String en) {
    }

    public static MatchExercisesResponse from(List<ExerciseMatchService.Match> matches) {
        return new MatchExercisesResponse(matches.stream().map(m -> {
            var entry = m.exerciseId() == null ? null : ExerciseCatalog.get().byId(m.exerciseId()).orElse(null);
            return entry == null ? new Item(m.name(), null, null, null)
                    : new Item(m.name(), entry.id(), entry.nb(), entry.en());
        }).toList());
    }
}
