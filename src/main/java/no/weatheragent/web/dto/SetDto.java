package no.weatheragent.web.dto;

import no.weatheragent.training.ExerciseSet;

/** Ett sett slik API-et eksponerer det. */
public record SetDto(String exercise, int reps, double weightKg) {

    public static SetDto from(ExerciseSet s) {
        return new SetDto(s.getExercise(), s.getReps(), s.getWeightKg());
    }
}
