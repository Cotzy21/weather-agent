package no.weatheragent.nutrition;

import java.util.List;
import java.util.Map;

/**
 * Én matvare fra Matvaretabellen (Mattilsynet) - VÅRT rene domeneobjekt,
 * ikke API-ets rå JSON. Alle næringsverdier er PER 100 GRAM spiselig vare.
 *
 * @param foodId           Matvaretabellens id, f.eks. "05.049"
 * @param name             norsk navn, f.eks. "Brød, fint, hjemmebakt"
 * @param kcalPer100g      kalorier per 100 g
 * @param nutrientsPer100g nærings-id -> mengde per 100 g (enheten er fast per
 *                         nærings-id, se {@link NutrientReference})
 * @param portions         vanlige porsjoner for varen (dl, skive, glass …)
 *                         med gramvekt - så mengder kan angis naturlig
 * @param searchKeywords   ekstra søkeord fra Matvaretabellen
 */
public record FoodItem(
        String foodId,
        String name,
        double kcalPer100g,
        Map<String, Double> nutrientsPer100g,
        List<Portion> portions,
        List<String> searchKeywords
) {

    /** En vanlig porsjon, f.eks. "skive" = 45 g eller "desiliter" = 100 g. */
    public record Portion(String name, String unit, double grams) {
    }

    /** Mengden av en næringsstoff per 100 g, eller 0 om ukjent. */
    public double nutrient(String nutrientId) {
        return nutrientsPer100g.getOrDefault(nutrientId, 0.0);
    }
}
