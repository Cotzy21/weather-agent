package no.weatheragent.nutrition;

import java.util.List;
import java.util.Map;

/**
 * Engelsk visningsnavn, konsekvens og kildeliste for hvert næringsstoff i {@link NutrientReference#TRACKED}.
 * Kildelistene står i SAMME REKKEFØLGE og har samme lengde som de norske: allergi-/diettfilteret ({@link FoodFlags})
 * kjenner bare de norske matnavnene, så vi filtrerer på norsk og henter det engelske navnet på samme plass.
 * {@code NutrientTranslationsTest} sikrer at listene henger sammen.
 */
final class NutrientTranslations {

    record En(String name, String consequence, List<String> sources) {
    }

    private NutrientTranslations() {
    }

    static En of(String nutrientId) {
        return BY_ID.get(nutrientId);
    }

    static final Map<String, En> BY_ID = Map.ofEntries(
            Map.entry("Fiber", new En("Fiber",
                    "Low fiber can cause sluggish digestion and affects blood sugar and cholesterol.",
                    List.of("wholegrain bread", "oats", "beans", "lentils", "fruit", "vegetables"))),
            Map.entry("Vit A", new En("Vitamin A",
                    "Low vitamin A can impair night vision and the immune system.",
                    List.of("carrots", "sweet potato", "spinach", "liver", "eggs", "dairy products"))),
            Map.entry("Vit B1", new En("Thiamin (B1)",
                    "Low thiamin can cause tiredness and difficulty concentrating.",
                    List.of("wholegrain", "pork", "legumes", "sunflower seeds", "oats"))),
            Map.entry("Vit B2", new En("Riboflavin (B2)",
                    "Low riboflavin can cause cracked lips and sore corners of the mouth.",
                    List.of("milk", "eggs", "almonds", "mushrooms", "green vegetables"))),
            Map.entry("Vit B6", new En("Vitamin B6",
                    "Low B6 can affect mood and the immune system.",
                    List.of("poultry", "fish", "banana", "potato", "chickpeas"))),
            Map.entry("Vit B12", new En("Vitamin B12",
                    "Low B12 can cause tiredness and numbness, and is common on a plant-based diet.",
                    List.of("meat", "fish", "eggs", "dairy products", "B12-fortified plant drink", "B12 supplement"))),
            Map.entry("Folat", new En("Folate",
                    "Low folate can cause anemia; extra important during pregnancy.",
                    List.of("leafy greens", "beans", "lentils", "orange", "avocado"))),
            Map.entry("Vit C", new En("Vitamin C",
                    "Low vitamin C weakens the immune system and iron absorption.",
                    List.of("bell pepper", "citrus", "berries", "broccoli", "kiwi"))),
            Map.entry("Vit D", new En("Vitamin D",
                    "Low vitamin D weakens the skeleton and the immune system - common in Norway in winter.",
                    List.of("fatty fish", "cod liver oil", "eggs", "fortified milk", "vitamin D-fortified plant drink",
                            "vitamin D supplement"))),
            Map.entry("Ca", new En("Calcium",
                    "Low calcium weakens the skeleton over time.",
                    List.of("milk", "cheese", "yogurt", "kale", "broccoli", "almonds",
                            "calcium-fortified plant drink", "sardines"))),
            Map.entry("Fe", new En("Iron",
                    "Low iron can cause anemia with tiredness and dizziness. Preferably have some vitamin C with it.",
                    List.of("red meat", "liver", "beans", "lentils", "spinach", "pumpkin seeds"))),
            Map.entry("Mg", new En("Magnesium",
                    "Low magnesium can cause muscle cramps and tiredness.",
                    List.of("nuts", "wholegrain", "beans", "dark chocolate", "pumpkin seeds"))),
            Map.entry("K", new En("Potassium",
                    "Low potassium can affect blood pressure and muscle function.",
                    List.of("potato", "banana", "beans", "fish", "tomato"))),
            Map.entry("Zn", new En("Zinc",
                    "Low zinc can weaken the immune system and wound healing.",
                    List.of("meat", "shellfish", "pumpkin seeds", "nuts", "oats", "chickpeas"))),
            Map.entry("Se", new En("Selenium",
                    "Low selenium can weaken the immune system and thyroid function.",
                    List.of("fish", "eggs", "Brazil nuts (1-2 a day is enough)", "sunflower seeds", "mushrooms"))),
            Map.entry("I", new En("Iodine",
                    "Low iodine affects the thyroid; common with little fish or dairy in the diet.",
                    List.of("white fish", "milk", "eggs", "iodized salt", "iodine supplement"))));
}
