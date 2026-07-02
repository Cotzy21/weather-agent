package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Oversetter Matvaretabellens rå JSON (foods.json) til våre {@link FoodItem}.
 * Holdt adskilt fra HTTP-klienten så tolkningen kan testes mot et fast
 * JSON-eksempel, samme mønster som vær-parserne.
 *
 * API-svaret ser forenklet slik ut (per matvare):
 * <pre>
 * foodId        : "05.049"
 * foodName      : "Brød, fint"
 * calories      : { quantity: 310, unit: "kcal" }   (per 100 g)
 * portions[]    : { portionName: "skive", portionUnit: "stk", quantity: 45.0, unit: "g" }
 * constituents[]: { nutrientId: "Protein", quantity: 19.9, unit: "g" }
 *                 (quantity mangler når verdien er ukjent - da hopper vi over)
 * searchKeywords: ["loff", ...]
 * </pre>
 */
public final class MatvaretabellenParser {

    private MatvaretabellenParser() {
    }

    public static List<FoodItem> parse(JsonNode root) {
        List<FoodItem> foods = new ArrayList<>();
        for (JsonNode food : root.path("foods")) {
            foods.add(parseFood(food));
        }
        return foods;
    }

    private static FoodItem parseFood(JsonNode food) {
        Map<String, Double> nutrients = new HashMap<>();
        for (JsonNode c : food.path("constituents")) {
            if (c.has("quantity")) {
                nutrients.put(c.path("nutrientId").asText(), c.path("quantity").asDouble());
            }
        }

        List<FoodItem.Portion> portions = new ArrayList<>();
        for (JsonNode p : food.path("portions")) {
            portions.add(new FoodItem.Portion(
                    p.path("portionName").asText(),
                    p.path("portionUnit").asText(),
                    p.path("quantity").asDouble()));
        }

        List<String> keywords = new ArrayList<>();
        food.path("searchKeywords").forEach(k -> keywords.add(k.asText()));

        return new FoodItem(
                food.path("foodId").asText(),
                food.path("foodName").asText(),
                food.path("calories").path("quantity").asDouble(),
                Map.copyOf(nutrients),
                List.copyOf(portions),
                List.copyOf(keywords));
    }
}
