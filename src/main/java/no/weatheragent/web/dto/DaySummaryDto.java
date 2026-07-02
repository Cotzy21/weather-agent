package no.weatheragent.web.dto;

import no.weatheragent.nutrition.MealLogService;

import java.util.List;

/** Dagens dagbok: alle innslag pluss totalene frontenden viser øverst. */
public record DaySummaryDto(
        List<MealEntryDto> entries,
        double kcal,
        double proteinG,
        double fatG,
        double carbG
) {

    public static DaySummaryDto from(MealLogService.DaySummary day) {
        return new DaySummaryDto(
                day.entries().stream().map(MealEntryDto::from).toList(),
                day.kcal(), day.proteinG(), day.fatG(), day.carbG());
    }
}
