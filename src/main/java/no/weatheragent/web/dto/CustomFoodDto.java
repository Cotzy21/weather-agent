package no.weatheragent.web.dto;

import no.weatheragent.nutrition.CustomFood;

import java.util.UUID;

/** En av brukerens egne matvarer, slik «Mine matvarer»-lista viser den. */
public record CustomFoodDto(
        UUID id,
        String foodId,
        String name,
        String brand,
        String barcode,
        double kcalPer100g,
        double proteinPer100g,
        double fatPer100g,
        double carbPer100g,
        String portionName,
        Double portionGrams,
        boolean isPublic
) {

    public static CustomFoodDto from(CustomFood f) {
        return new CustomFoodDto(f.getId(), CustomFood.ID_PREFIX + f.getId(), f.getName(), f.getBrand(),
                f.getBarcode(), f.getKcalPer100g(), f.getProteinG(), f.getFatG(), f.getCarbG(),
                f.getPortionName(), f.getPortionGrams(), f.isPublic());
    }
}
