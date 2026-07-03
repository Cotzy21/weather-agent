package no.weatheragent.nutrition;

import no.weatheragent.training.Workout;
import no.weatheragent.training.WorkoutCalorieEstimator;
import no.weatheragent.training.WorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dagsbalansen - der trening og kosthold møtes: dagens loggede mat, kalorier
 * forbrent i dagens loggede økter, og (om profilen er satt) kalorimålet.
 *
 * Regnestykket følger samme konvensjon som kjente kalori-apper:
 *   igjen = mål − spist + trening
 * (trening «frigjør» kalorier, siden målet dekker hverdagsaktivitet, ikke økter).
 */
@Service
public class DailyBalanceService {

    /** Fra denne forbrenningen minner vi om påfyll etter økta. */
    private static final int BIG_BURN_KCAL = 500;

    private final MealLogService meals;
    private final CalorieGoalService goals;
    private final WorkoutRepository workouts;

    public DailyBalanceService(MealLogService meals, CalorieGoalService goals, WorkoutRepository workouts) {
        this.meals = meals;
        this.goals = goals;
        this.workouts = workouts;
    }

    /**
     * @param day          dagens mat + totaler
     * @param burnedKcal   estimert forbrenning i dagens loggede økter
     * @param targetKcal   anbefalt daglig inntak, eller null når profil mangler
     * @param remainingKcal mål − spist + trening, eller null når profil mangler
     * @param recoveryTip  påfyll-råd etter stor treningsdag, ellers null
     */
    public record DayBalance(MealLogService.DaySummary day, int burnedKcal,
                             Double targetKcal, Double remainingKcal, String recoveryTip) {
    }

    @Transactional(readOnly = true)
    public DayBalance day(UUID userId, LocalDate date) {
        MealLogService.DaySummary day = meals.day(userId, date);
        CalorieGoal goal = goals.find(userId).orElse(null);

        // Uten profil bruker vi en normalvekt i estimatet - grovt uansett.
        double weightKg = goal != null ? goal.getWeightKg() : 75;
        List<Workout> todays = workouts.findByUserIdAndDate(userId, date);
        int burned = todays.stream()
                .mapToInt(w -> WorkoutCalorieEstimator.estimate(w, weightKg))
                .sum();

        Double target = goal != null ? goals.dailyTargetKcal(goal) : null;
        Double remaining = target != null ? target - day.kcal() + burned : null;

        String tip = burned >= BIG_BURN_KCAL
                ? "Stor treningsdag (~" + burned + " kcal): fyll på med karbohydrater og 20-30 g "
                + "protein innen et par timer, og drikk godt utover kvelden."
                : null;

        return new DayBalance(day, burned, target, remaining, tip);
    }
}
