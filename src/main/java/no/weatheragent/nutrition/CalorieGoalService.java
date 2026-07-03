package no.weatheragent.nutrition;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kalorimålet: lagre profilen og regne ut anbefalt daglig inntak.
 *
 * Modellen (bevisst enkel og veletablert):
 *  - Hvilestoffskifte (BMR) med Mifflin-St Jeor:
 *      menn:    10·kg + 6.25·cm − 5·alder + 5
 *      kvinner: 10·kg + 6.25·cm − 5·alder − 161
 *  - Ganger en aktivitetsfaktor for HVERDAGEN (jobb, gange - ikke økter,
 *    de legges til per dag fra treningsloggen i dagsbalansen).
 *  - Vektmålet justerer: ±7700 kcal per kg kroppsvekt fordelt på uka
 *    (-0,5 kg/uke ≈ -550 kcal/dag).
 *  - Gulv på 1400 kcal/dag så aggressive mål ikke gir uforsvarlige tall.
 */
@Service
public class CalorieGoalService {

    static final Set<String> SEXES = Set.of("M", "K");
    static final Map<String, Double> ACTIVITY_FACTORS = Map.of(
            "ROLIG", 1.2,     // stillesittende
            "LETT", 1.375,    // litt gange/lett aktivitet
            "MODERAT", 1.55,  // aktiv hverdag
            "HØY", 1.725);    // fysisk krevende hverdag

    private static final double KCAL_PER_KG = 7700;
    private static final double MIN_DAILY_KCAL = 1400;
    private static final double MAX_ABS_GOAL_KG_PER_WEEK = 1.5;

    private final CalorieGoalRepository repository;

    public CalorieGoalService(CalorieGoalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CalorieGoal save(UUID userId, double weightKg, double heightCm, int age,
                            String sex, String activityLevel, double goalKgPerWeek) {
        if (!SEXES.contains(sex)) {
            throw new IllegalArgumentException("Kjønn må være M eller K.");
        }
        if (!ACTIVITY_FACTORS.containsKey(activityLevel)) {
            throw new IllegalArgumentException("Ukjent aktivitetsnivå: " + activityLevel);
        }
        if (weightKg < 30 || weightKg > 250 || heightCm < 120 || heightCm > 230
                || age < 15 || age > 100) {
            throw new IllegalArgumentException("Vekt, høyde eller alder er utenfor rimelig område.");
        }
        if (Math.abs(goalKgPerWeek) > MAX_ABS_GOAL_KG_PER_WEEK) {
            throw new IllegalArgumentException(
                    "Vektmål over " + MAX_ABS_GOAL_KG_PER_WEEK + " kg/uke er ikke sunt - velg et roligere tempo.");
        }
        return repository.save(new CalorieGoal(userId, weightKg, heightCm, age, sex, activityLevel, goalKgPerWeek));
    }

    @Transactional(readOnly = true)
    public Optional<CalorieGoal> find(UUID userId) {
        return repository.findById(userId);
    }

    /** Anbefalt daglig inntak (kcal) for en lagret profil. */
    public double dailyTargetKcal(CalorieGoal goal) {
        double bmr = 10 * goal.getWeightKg() + 6.25 * goal.getHeightCm() - 5 * goal.getAge()
                + ("M".equals(goal.getSex()) ? 5 : -161);
        double maintenance = bmr * ACTIVITY_FACTORS.get(goal.getActivityLevel());
        double adjustment = goal.getGoalKgPerWeek() * KCAL_PER_KG / 7.0;
        return Math.max(MIN_DAILY_KCAL, maintenance + adjustment);
    }
}
