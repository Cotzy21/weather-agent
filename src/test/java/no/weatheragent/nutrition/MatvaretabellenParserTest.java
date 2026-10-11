package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatvaretabellenParserTest {

    // Nedstrippet, men strukturelt ekte utdrag av foods.json.
    private static final String SAMPLE = """
            {"foods": [
              {
                "foodId": "01.291",
                "foodName": "Melk, uspesifisert",
                "calories": {"quantity": 47, "unit": "kcal"},
                "portions": [
                  {"portionName": "glass", "portionUnit": "stk", "quantity": 200.0, "unit": "g"},
                  {"portionName": "desiliter", "portionUnit": "dl", "quantity": 100.0, "unit": "g"}
                ],
                "constituents": [
                  {"nutrientId": "Protein", "quantity": 3.5, "unit": "g"},
                  {"nutrientId": "Fett", "quantity": 1.5, "unit": "g"},
                  {"nutrientId": "Karbo", "quantity": 4.6, "unit": "g"},
                  {"nutrientId": "Ca", "quantity": 124.0, "unit": "mg"},
                  {"nutrientId": "Vit E"}
                ],
                "searchKeywords": ["melk"]
              }
            ]}
            """;

    @Test
    void parsesFoodWithNutrientsAndPortions() throws Exception {
        JsonNode root = new ObjectMapper().readTree(SAMPLE);

        List<FoodItem> foods = MatvaretabellenParser.parse(root);

        assertEquals(1, foods.size());
        FoodItem melk = foods.getFirst();
        assertEquals("01.291", melk.foodId());
        assertEquals("Melk, uspesifisert", melk.name());
        assertEquals(47, melk.kcalPer100g());
        assertEquals(3.5, melk.nutrient("Protein"));
        assertEquals(124.0, melk.nutrient("Ca"));
        assertEquals(2, melk.portions().size());
        assertEquals("glass", melk.portions().getFirst().name());
        assertEquals(200.0, melk.portions().getFirst().grams());
    }

    @Test
    void nutrientWithoutQuantityIsSkippedAndReadsAsZero() throws Exception {
        JsonNode root = new ObjectMapper().readTree(SAMPLE);

        FoodItem melk = MatvaretabellenParser.parse(root).getFirst();

        // "Vit E" mangler quantity i kilden -> skal ikke inn i kartet, leses som 0.
        assertTrue(!melk.nutrientsPer100g().containsKey("Vit E"));
        assertEquals(0.0, melk.nutrient("Vit E"));
    }

    @Test
    void streamingMatchesExistingNutrientsPortionsAndKeywords() throws Exception {
        assertEquals(MatvaretabellenParser.parse(new ObjectMapper().readTree(SAMPLE)), stream(SAMPLE));
    }

    @Test
    void skipsUnneededMetadataBeforeAndAfterFoods() throws Exception {
        assertEquals(stream(SAMPLE), stream(SAMPLE.replace("{\"foods\":", "{\"metadata\":{\"foods\":[1]},\"foods\":")
                .stripTrailing().replaceFirst("}$", ",\"other\":[{\"large\":\"ignored\"}]}")));
    }

    @Test
    void rejectsMissingMalformedTruncatedOrDuplicateFoodArray() {
        for (String json : List.of("{}", "[]", "{\"foods\":null}", "{\"foods\":[null]}",
                "{\"foods\":[{}", "{\"foods\":[],\"foods\":[]}", "{\"foods\":[]} {}")) {
            assertThrows(IOException.class, () -> stream(json), json);
        }
    }

    @Test
    void limitsRetainedFoodEntries() {
        assertThrows(IOException.class, () -> stream("{\"foods\":[" + "{},".repeat(10_000) + "{}]}"));
    }

    private static List<FoodItem> stream(String json) throws IOException {
        return MatvaretabellenParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
