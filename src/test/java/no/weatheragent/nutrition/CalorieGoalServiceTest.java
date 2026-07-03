package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalorieGoalServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final CalorieGoalRepository repository = mock(CalorieGoalRepository.class);
    private final CalorieGoalService service = new CalorieGoalService(repository);

    private CalorieGoal goal(String sex, String activity, double goalKgPerWeek) {
        return new CalorieGoal(USER, 80, 180, 25, sex, activity, goalKgPerWeek);
    }

    @Test
    void computesMifflinStJeorTargetForMan() {
        // BMR: 10·80 + 6.25·180 − 5·25 + 5 = 1805; ·1.55 (MODERAT) = 2797.75.
        assertEquals(2797.75, service.dailyTargetKcal(goal("M", "MODERAT", 0)), 0.01);
    }

    @Test
    void weightLossGoalSubtractsDailyDeficit() {
        // −0.5 kg/uke = −7700/2/7 = −550 kcal/dag.
        assertEquals(2247.75, service.dailyTargetKcal(goal("M", "MODERAT", -0.5)), 0.01);
    }

    @Test
    void womanFormulaDiffersByConstant() {
        // Kvinne: −161 i stedet for +5 -> 166 lavere BMR før faktor.
        double man = service.dailyTargetKcal(goal("M", "ROLIG", 0));
        double woman = service.dailyTargetKcal(goal("K", "ROLIG", 0));
        assertEquals(166 * 1.2, man - woman, 0.01);
    }

    @Test
    void targetNeverGoesBelowFloor() {
        CalorieGoal small = new CalorieGoal(USER, 45, 150, 90, "K", "ROLIG", -1.5);
        assertEquals(1400, service.dailyTargetKcal(small), 0.01);
    }

    @Test
    void rejectsUnhealthyGoalPace() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(USER, 80, 180, 25, "M", "MODERAT", -2.0));
    }

    @Test
    void rejectsUnknownActivityLevelAndSex() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(USER, 80, 180, 25, "M", "SOFA", 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(USER, 80, 180, 25, "X", "MODERAT", 0));
    }
}
