package no.weatheragent.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import no.weatheragent.training.Workout;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryAdvisorTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 4);

    private static Workout workout(String type, LocalDate date) {
        return new Workout(USER, date, "Økt", type, JsonNodeFactory.instance.objectNode(), null);
    }

    @Test
    void advisesForTodaysAndYesterdaysWorkoutsOnly() {
        List<RecoveryAdvice> advice = RecoveryAdvisor.advise(List.of(
                workout("LØPING", TODAY),
                workout("STYRKE", TODAY.minusDays(1)),
                workout("HIKING", TODAY.minusDays(3))), TODAY); // for gammel

        assertEquals(2, advice.size());
        assertEquals("LØPING", advice.get(0).type());
        assertEquals("i dag", advice.get(0).when());
        assertEquals("STYRKE", advice.get(1).type());
        assertEquals("i går", advice.get(1).when());
    }

    @Test
    void duplicateTypeGivesOneCard() {
        List<RecoveryAdvice> advice = RecoveryAdvisor.advise(List.of(
                workout("STYRKE", TODAY),
                workout("STYRKE", TODAY.minusDays(1))), TODAY);

        assertEquals(1, advice.size());
    }

    @Test
    void englishFlagGivesEnglishSteps() {
        List<RecoveryAdvice> no = RecoveryAdvisor.advise(List.of(workout("STYRKE", TODAY)), TODAY, false);
        List<RecoveryAdvice> en = RecoveryAdvisor.advise(List.of(workout("STYRKE", TODAY)), TODAY, true);

        assertTrue(no.getFirst().steps().stream().anyMatch(s -> s.contains("søvn")));
        assertTrue(en.getFirst().steps().stream().anyMatch(s -> s.toLowerCase().contains("sleep")));
    }

    @Test
    void unknownTypeFallsBackToGeneralSteps() {
        List<RecoveryAdvice> advice = RecoveryAdvisor.advise(
                List.of(workout("YOGA", TODAY)), TODAY);

        assertEquals(1, advice.size());
        assertTrue(advice.getFirst().steps().stream().anyMatch(s -> s.contains("søvn")));
    }

    @Test
    void streakCountsConsecutiveDaysBackFromToday() {
        int streak = RecoveryAdvisor.trainingStreak(List.of(
                workout("STYRKE", TODAY),
                workout("LØPING", TODAY.minusDays(1)),
                workout("STYRKE", TODAY.minusDays(2)),
                workout("STYRKE", TODAY.minusDays(4))), TODAY); // hull på dag 3

        assertEquals(3, streak);
    }

    @Test
    void streakStartsFromYesterdayWhenTodayIsRestSoFar() {
        int streak = RecoveryAdvisor.trainingStreak(List.of(
                workout("STYRKE", TODAY.minusDays(1)),
                workout("STYRKE", TODAY.minusDays(2))), TODAY);

        assertEquals(2, streak);
    }
}
