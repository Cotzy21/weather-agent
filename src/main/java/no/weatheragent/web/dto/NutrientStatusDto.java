package no.weatheragent.web.dto;

import no.weatheragent.nutrition.MealLogService;

/**
 * Ett næringsstoff i ukesoversikten. {@code advice} er null når inntaket er
 * greit - frontenden viser råd bare når det faktisk er noe å ta tak i.
 */
public record NutrientStatusDto(
        String nutrientId,
        String name,
        String unit,
        double avgPerDay,
        double dailyTarget,
        double percent,
        String advice
) {

    public static NutrientStatusDto from(MealLogService.NutrientStatus status) {
        return new NutrientStatusDto(
                status.reference().nutrientId(),
                status.reference().displayName(),
                status.reference().unit(),
                status.avgPerDay(),
                status.reference().dailyTarget(),
                status.percent(),
                status.advice());
    }
}
