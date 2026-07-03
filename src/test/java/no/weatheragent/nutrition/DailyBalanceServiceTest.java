package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.training.Workout;
import no.weatheragent.training.WorkoutRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyBalanceServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 7, 2);

    private final MealLogService meals = mock(MealLogService.class);
    private final CalorieGoalService goals = mock(CalorieGoalService.class);
    private final WorkoutRepository workouts = mock(WorkoutRepository.class);
    private final DailyBalanceService service = new DailyBalanceService(meals, goals, workouts);

    private static Workout run(String contentJson) {
        try {
            return new Workout(USER, DATE, "Løpetur", "LØPING",
                    new ObjectMapper().readTree(contentJson), null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void combinesFoodTrainingAndGoalIntoRemaining() {
        when(meals.day(USER, DATE)).thenReturn(
                new MealLogService.DaySummary(List.of(), 1500, 100, 50, 150));
        CalorieGoal goal = new CalorieGoal(USER, 80, 180, 25, "M", "MODERAT", 0);
        when(goals.find(USER)).thenReturn(Optional.of(goal));
        when(goals.dailyTargetKcal(goal)).thenReturn(2800.0);
        // 10 km/t i én time med 80 kg -> 800 kcal.
        when(workouts.findByUserIdAndDate(USER, DATE)).thenReturn(
                List.of(run("{\"distanceKm\":10,\"durationMin\":60}")));

        DailyBalanceService.DayBalance balance = service.day(USER, DATE);

        assertEquals(800, balance.burnedKcal());
        assertEquals(2800.0, balance.targetKcal());
        assertEquals(2800 - 1500 + 800, balance.remainingKcal(), 0.01);
        assertNotNull(balance.recoveryTip()); // 800 kcal er en stor treningsdag
    }

    @Test
    void withoutGoalTargetAndRemainingAreNull() {
        when(meals.day(USER, DATE)).thenReturn(
                new MealLogService.DaySummary(List.of(), 500, 30, 20, 60));
        when(goals.find(USER)).thenReturn(Optional.empty());
        when(workouts.findByUserIdAndDate(USER, DATE)).thenReturn(List.of());

        DailyBalanceService.DayBalance balance = service.day(USER, DATE);

        assertEquals(0, balance.burnedKcal());
        assertNull(balance.targetKcal());
        assertNull(balance.remainingKcal());
        assertNull(balance.recoveryTip());
    }
}
