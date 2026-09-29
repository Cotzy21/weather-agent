package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.training.NextSetSuggestion.Action;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionAdvisorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    /** En styrkeøkt med én øvelse; sets som "vekt×reps", f.eks. "80x5". */
    private static Workout strength(LocalDate date, String exercise, String... sets) {
        StringBuilder json = new StringBuilder("{\"blocks\":[{\"kind\":\"exercise\",\"name\":\"")
                .append(exercise).append("\",\"sets\":[");
        for (int i = 0; i < sets.length; i++) {
            String[] parts = sets[i].split("x");
            json.append(i > 0 ? "," : "")
                    .append("{\"weightKg\":").append(parts[0]).append(",\"reps\":").append(parts[1]).append('}');
        }
        json.append("]}]}");
        return workout(date, json.toString());
    }

    private static Workout workout(LocalDate date, String contentJson) {
        try {
            return new Workout(USER, date, "Økt", "STYRKE", MAPPER.readTree(contentJson), null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static NextSetSuggestion only(List<Workout> workouts) {
        List<NextSetSuggestion> out = ProgressionAdvisor.suggest(workouts, TODAY, false);
        assertEquals(1, out.size());
        return out.getFirst();
    }

    @Test
    void allSetsAtTopOfRangeIncreasesWeight() {
        NextSetSuggestion s = only(List.of(strength(TODAY.minusDays(3), "Benkpress", "80x5", "80x5", "80x5")));

        assertEquals(Action.OK_VEKT, s.action());
        assertEquals(82.5, s.nextWeightKg());
        assertEquals(3, s.nextReps()); // bunnen av 3-5-området
        assertEquals(3, s.sets());
        assertTrue(s.reason().contains("82,5 kg")); // norsk desimalkomma
    }

    @Test
    void bigLowerBodyLiftsJumpFiveKilos() {
        NextSetSuggestion s = only(List.of(strength(TODAY.minusDays(2), "Knebøy", "100x5", "100x5", "100x5")));

        assertEquals(105, s.nextWeightKg());
    }

    @Test
    void lightWeightsJumpOneKilo() {
        NextSetSuggestion s = only(List.of(strength(TODAY.minusDays(2), "Sidehev", "8x12", "8x12", "8x12")));

        assertEquals(Action.OK_VEKT, s.action());
        assertEquals(9, s.nextWeightKg());
        assertEquals(8, s.nextReps()); // bunnen av 8-12
    }

    @Test
    void notAllSetsAtTopKeepsWeightAndAddsARep() {
        NextSetSuggestion s = only(List.of(strength(TODAY.minusDays(2), "Benkpress", "80x6", "80x6", "80x6")));

        assertEquals(Action.FLERE_REPS, s.action());
        assertEquals(80, s.nextWeightKg());
        assertEquals(7, s.nextReps()); // mot toppen av 6-8
    }

    @Test
    void warmUpSetsAreIgnored() {
        NextSetSuggestion s = only(List.of(
                strength(TODAY.minusDays(1), "Benkpress", "40x10", "60x8", "80x5", "80x5", "80x5")));

        assertEquals(Action.OK_VEKT, s.action());
        assertEquals(List.of(5, 5, 5), s.lastReps());
        assertEquals(3, s.sets());
    }

    @Test
    void threeSessionsWithoutProgressSuggestsDeload() {
        NextSetSuggestion s = only(List.of(
                strength(TODAY.minusDays(10), "Benkpress", "80x6", "80x6", "80x5"),
                strength(TODAY.minusDays(6), "Benkpress", "80x6", "80x5", "80x5"),
                strength(TODAY.minusDays(2), "Benkpress", "80x6", "80x5", "80x5")));

        assertEquals(Action.DELOAD, s.action());
        assertEquals(72.5, s.nextWeightKg()); // 80 · 0,9 = 72 -> nærmeste 2,5
        assertEquals(8, s.nextReps());
    }

    @Test
    void progressingRepsIsNotAStall() {
        NextSetSuggestion s = only(List.of(
                strength(TODAY.minusDays(10), "Benkpress", "80x6", "80x6", "80x5"),
                strength(TODAY.minusDays(6), "Benkpress", "80x6", "80x6", "80x6"),
                strength(TODAY.minusDays(2), "Benkpress", "80x7", "80x6", "80x6")));

        assertEquals(Action.FLERE_REPS, s.action());
    }

    @Test
    void bodyweightExercisesAddReps() {
        NextSetSuggestion s = only(List.of(strength(TODAY.minusDays(1), "Pull-ups", "0x8", "0x7")));

        assertEquals(Action.KROPPSVEKT, s.action());
        assertEquals(9, s.nextReps());
    }

    @Test
    void exercisesNotTrainedForEightWeeksAreDropped() {
        List<NextSetSuggestion> out = ProgressionAdvisor.suggest(List.of(
                strength(TODAY.minusDays(70), "Benkpress", "80x5"),
                strength(TODAY.minusDays(3), "Knebøy", "100x5")), TODAY, false);

        assertEquals(1, out.size());
        assertEquals("Knebøy", out.getFirst().exercise());
    }

    @Test
    void dropsetProgressesOnTheTopSetOnly() {
        NextSetSuggestion s = only(List.of(workout(TODAY.minusDays(1), """
                {"blocks":[{"kind":"dropset","name":"Bicepscurl",
                  "drops":[{"weightKg":20,"reps":12},{"weightKg":15,"reps":10},{"weightKg":10,"reps":8}]}]}
                """)));

        assertEquals(20, s.lastWeightKg());
        assertEquals(List.of(12), s.lastReps());
        assertEquals(Action.OK_VEKT, s.action());
    }

    @Test
    void englishReasonsUseEnglishAndDecimalPoint() {
        List<NextSetSuggestion> out = ProgressionAdvisor.suggest(
                List.of(strength(TODAY.minusDays(1), "Benkpress", "80x5", "80x5")), TODAY, true);

        assertTrue(out.getFirst().reason().contains("go up to 82.5 kg"));
    }
}
