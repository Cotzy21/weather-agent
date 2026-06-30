package no.weatheragent.web.dto;

import no.weatheragent.training.ExerciseSet;
import no.weatheragent.training.Workout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** En logget treningsøkt slik API-et eksponerer den. */
public record WorkoutDto(UUID id, LocalDate date, String title, List<SetDto> sets, Instant createdAt) {

    public static WorkoutDto from(Workout w) {
        List<SetDto> sets = w.getSets().stream()
                .sorted(Comparator.comparingInt(ExerciseSet::getPosition))
                .map(SetDto::from)
                .toList();
        return new WorkoutDto(w.getId(), w.getDate(), w.getTitle(), sets, w.getCreatedAt());
    }
}
