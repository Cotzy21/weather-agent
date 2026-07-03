package no.weatheragent.web.dto;

import no.weatheragent.nutrition.CalorieGoal;

/** Brukerens kalorimål-profil pluss det utregnede daglige målet. */
public record CalorieGoalDto(
        double weightKg,
        double heightCm,
        int age,
        String sex,
        String activityLevel,
        double goalKgPerWeek,
        double dailyTargetKcal
) {

    public static CalorieGoalDto from(CalorieGoal goal, double dailyTargetKcal) {
        return new CalorieGoalDto(goal.getWeightKg(), goal.getHeightCm(), goal.getAge(),
                goal.getSex(), goal.getActivityLevel(), goal.getGoalKgPerWeek(), dailyTargetKcal);
    }
}
