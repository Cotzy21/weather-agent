package no.weatheragent.web.dto;

import no.weatheragent.nutrition.FoodItem;

import java.util.List;

/**
 * Et søketreff fra Matvaretabellen slik frontenden trenger det: nok til å vise
 * varen, velge porsjon og forhåndsvise kalorier - resten beregnes ved logging.
 */
public record FoodDto(
        String foodId,
        String name,
        double kcalPer100g,
        double proteinPer100g,
        double fatPer100g,
        double carbPer100g,
        List<PortionDto> portions
) {

    public record PortionDto(String name, double grams) {
    }

    public static FoodDto from(FoodItem food) {
        return new FoodDto(
                food.foodId(),
                food.name(),
                food.kcalPer100g(),
                food.nutrient("Protein"),
                food.nutrient("Fett"),
                food.nutrient("Karbo"),
                food.portions().stream()
                        .map(p -> new PortionDto(p.name(), p.grams()))
                        .toList());
    }
}
