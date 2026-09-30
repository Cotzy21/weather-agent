package no.weatheragent.training;

import java.time.LocalDate;
import java.util.List;

/**
 * Alt appen vet om brukerens trening: det brukeren har sagt (liker / liker ikke /
 * notater) og vanene regnet ut fra øktene.
 *
 * @param usualDays ISO-ukedager (1 = mandag) brukeren oftest trener på
 */
public record TrainingMemoryProfile(
        List<String> liked,
        List<String> disliked,
        String notes,
        int workoutsLast28Days,
        double avgPerWeek,
        List<Integer> usualDays,
        List<String> topExercises,
        LocalDate lastWorkout) {
}
