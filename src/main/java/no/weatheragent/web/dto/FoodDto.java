package no.weatheragent.web.dto;

import no.weatheragent.nutrition.FoodItem;

import java.util.List;

/**
 * Et søketreff slik frontenden trenger det: nok til å vise varen, velge porsjon
 * og forhåndsvise kalorier - resten beregnes ved logging.
 *
 * {@code source} sier hvor varen kommer fra, så UI-et kan merke den:
 * MATVARETABELLEN, EGEN (brukerens egen) eller OFFENTLIG (delt av en annen bruker).
 * {@code warnings} er advarsler mot brukerens preferanser (se FoodFlags):
 * "ALLERGEN:MELK", "DIETT", "MISLIKER" - tom når alt er greit.
 */
public record FoodDto(
        String foodId,
        String name,
        double kcalPer100g,
        double proteinPer100g,
        double fatPer100g,
        double carbPer100g,
        List<PortionDto> portions,
        String source,
        List<String> warnings
) {

    public record PortionDto(String name, double grams) {
    }

    public static FoodDto from(FoodItem food) {
        return from(food, "MATVARETABELLEN");
    }

    public static FoodDto from(FoodItem food, String source) {
        return new FoodDto(
                food.foodId(),
                food.name(),
                food.kcalPer100g(),
                food.nutrient("Protein"),
                food.nutrient("Fett"),
                food.nutrient("Karbo"),
                food.portions().stream()
                        .map(p -> new PortionDto(p.name(), p.grams()))
                        .toList(),
                source,
                List.of());
    }

    public FoodDto withWarnings(List<String> newWarnings) {
        return new FoodDto(foodId, name, kcalPer100g, proteinPer100g, fatPer100g, carbPer100g,
                portions, source, List.copyOf(newWarnings));
    }
}
