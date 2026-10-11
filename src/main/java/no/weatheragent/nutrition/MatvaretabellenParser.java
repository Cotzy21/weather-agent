package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

import java.io.IOException;
import java.io.InputStream;

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

    private static final ObjectMapper STREAM_MAPPER = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(32L * 1024 * 1024).maxStringLength(100_000).build())
            .build());
    private static final int MAX_FOODS = 10_000;

    private MatvaretabellenParser() {
    }

    /** Keep only one food's JSON tree in memory, rather than the entire 14 MB document. */
    public static List<FoodItem> parse(InputStream input) throws IOException {
        List<FoodItem> foods = new ArrayList<>();
        boolean found = false;
        try (var parser = STREAM_MAPPER.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IOException("Expected food document object");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw new IOException("Invalid food document");
                String field = parser.currentName();
                parser.nextToken();
                if (!"foods".equals(field)) {
                    parser.skipChildren();
                    continue;
                }
                if (found || parser.currentToken() != JsonToken.START_ARRAY) throw new IOException("Invalid foods array");
                found = true;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) throw new IOException("Invalid food entry");
                    if (foods.size() >= MAX_FOODS) throw new IOException("Too many food entries");
                    foods.add(parseFood(STREAM_MAPPER.readTree(parser)));
                }
            }
            if (!found || parser.nextToken() != null) throw new IOException("Invalid food document");
        }
        return List.copyOf(foods);
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
