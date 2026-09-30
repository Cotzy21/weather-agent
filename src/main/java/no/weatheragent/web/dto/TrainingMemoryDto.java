package no.weatheragent.web.dto;

import no.weatheragent.training.TrainingMemoryProfile;

import java.time.LocalDate;
import java.util.List;

/** Treningsminnet ut: det brukeren har sagt + vaner regnet ut fra øktene. */
public record TrainingMemoryDto(
        List<String> liked,
        List<String> disliked,
        String notes,
        int workoutsLast28Days,
        double avgPerWeek,
        List<Integer> usualDays,
        List<String> topExercises,
        LocalDate lastWorkout) {

    public static TrainingMemoryDto from(TrainingMemoryProfile p) {
        return new TrainingMemoryDto(p.liked(), p.disliked(), p.notes(), p.workoutsLast28Days(),
                p.avgPerWeek(), p.usualDays(), p.topExercises(), p.lastWorkout());
    }
}
