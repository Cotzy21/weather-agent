package no.weatheragent.nutrition;

/**
 * Et produkt fra Open Food Facts, redusert til det appen bruker. Næring er per 100 g (eller 100 ml, som vi
 * regner som 100 g). {@code portionGrams} er null når produktet ikke oppgir en porsjonsstørrelse.
 */
public record OpenFoodFactsProduct(String name, String brand, double kcalPer100g, double proteinG, double fatG,
                                   double carbG, Double portionGrams) {
}
