package no.weatheragent.web.dto;

import no.weatheragent.nutrition.MealEntry;

import java.time.LocalDate;
import java.util.UUID;

/** Én logget matvare i dagboka. */
public record MealEntryDto(
        UUID id,
        LocalDate date,
        String meal,
        String foodName,
        double grams,
        double kcal,
        double proteinG,
        double fatG,
        double carbG
) {

    public static MealEntryDto from(MealEntry e) {
        return new MealEntryDto(e.getId(), e.getDate(), e.getMeal(), e.getFoodName(),
                e.getGrams(), e.getKcal(), e.getProteinG(), e.getFatG(), e.getCarbG());
    }
}
