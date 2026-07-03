package no.weatheragent.web.dto;

import no.weatheragent.nutrition.DailyBalanceService;

import java.util.List;

/**
 * Dagens dagbok: innslag, mat-totaler OG balansen mot trening/mål.
 * targetKcal/remainingKcal er null til brukeren har satt opp kalorimålet;
 * recoveryTip er null unntatt etter store treningsdager.
 */
public record DaySummaryDto(
        List<MealEntryDto> entries,
        double kcal,
        double proteinG,
        double fatG,
        double carbG,
        int burnedKcal,
        Double targetKcal,
        Double remainingKcal,
        String recoveryTip
) {

    public static DaySummaryDto from(DailyBalanceService.DayBalance balance) {
        var day = balance.day();
        return new DaySummaryDto(
                day.entries().stream().map(MealEntryDto::from).toList(),
                day.kcal(), day.proteinG(), day.fatG(), day.carbG(),
                balance.burnedKcal(), balance.targetKcal(), balance.remainingKcal(),
                balance.recoveryTip());
    }
}
