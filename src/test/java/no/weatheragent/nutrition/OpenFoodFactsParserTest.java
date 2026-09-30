package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenFoodFactsParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Optional<OpenFoodFactsProduct> parse(String json) throws Exception {
        return OpenFoodFactsParser.parse(JSON.readTree(json));
    }

    /** Et ekte svar fra Open Food Facts (Prince-kjeks), forkortet til feltene vi ber om. */
    private static final String PRINCE = """
            {"code":"7622210449283","status":1,"product":{"brands":"Mondelez Schweiz GmbH","product_name":"Prince",
             "serving_quantity":20,"nutriments":{"energy-kcal_100g":467,"energy-kj_100g":1962,"energy_100g":1962,
             "proteins_100g":6.3,"fat_100g":17,"carbohydrates_100g":69,"sugars_100g":32}}}
            """;

    @Test
    void readsARealProduct() throws Exception {
        OpenFoodFactsProduct p = parse(PRINCE).orElseThrow();

        assertEquals("Prince", p.name());
        assertEquals("Mondelez Schweiz GmbH", p.brand());
        assertEquals(467, p.kcalPer100g());
        assertEquals(6.3, p.proteinG());
        assertEquals(17, p.fatG());
        assertEquals(69, p.carbG());
        assertEquals(20.0, p.portionGrams());
    }

    @Test
    void unknownProductsGiveNothing() throws Exception {
        assertTrue(parse("{\"status\":0,\"status_verbose\":\"product not found\"}").isEmpty());
        assertTrue(parse("{}").isEmpty());
        assertTrue(OpenFoodFactsParser.parse(null).isEmpty());
    }

    @Test
    void aProductWithoutANameIsUselessInTheDiary() throws Exception {
        assertTrue(parse("{\"status\":1,\"product\":{\"product_name\":\"  \",\"nutriments\":{\"energy-kcal_100g\":100}}}").isEmpty());
    }

    @Test
    void fallsBackToTheGenericNameAndConvertsKilojoulesWhenKcalIsMissing() throws Exception {
        OpenFoodFactsProduct p = parse("""
                {"status":1,"product":{"generic_name":"Havregrøt","nutriments":{"energy-kj_100g":1046}}}
                """).orElseThrow();

        assertEquals("Havregrøt", p.name());
        assertEquals(250, p.kcalPer100g()); // 1046 kJ / 4.184
        assertEquals(0, p.proteinG());       // manglende makroer blir 0, ikke en feil
    }

    @Test
    void acceptsNumbersSentAsText() throws Exception {
        OpenFoodFactsProduct p = parse("""
                {"status":1,"product":{"product_name":"Melk","nutriments":{"energy-kcal_100g":"46,5","proteins_100g":"3.4","fat_100g":"1.5","carbohydrates_100g":"4.8"}}}
                """).orElseThrow();

        assertEquals(46.5, p.kcalPer100g());
        assertEquals(3.4, p.proteinG());
    }

    @Test
    void rejectsImpossibleNumbersLikeOurOwnFoodsDo() throws Exception {
        // over 900 kcal per 100 g
        assertTrue(parse("{\"status\":1,\"product\":{\"product_name\":\"X\",\"nutriments\":{\"energy-kcal_100g\":1200}}}").isEmpty());
        // makroene veier mer enn varen
        assertTrue(parse("{\"status\":1,\"product\":{\"product_name\":\"X\",\"nutriments\":{\"energy-kcal_100g\":400,\"proteins_100g\":60,\"fat_100g\":40,\"carbohydrates_100g\":30}}}").isEmpty());
        // negative verdier
        assertTrue(parse("{\"status\":1,\"product\":{\"product_name\":\"X\",\"nutriments\":{\"energy-kcal_100g\":100,\"fat_100g\":-3}}}").isEmpty());
        // ingen energi i det hele tatt
        assertTrue(parse("{\"status\":1,\"product\":{\"product_name\":\"X\",\"nutriments\":{\"proteins_100g\":5}}}").isEmpty());
    }

    @Test
    void keepsOnlyTheFirstBrandAndCutsLongNames() throws Exception {
        JsonNode node = JSON.readTree("{\"status\":1,\"product\":{\"product_name\":\"" + "N".repeat(300)
                + "\",\"brands\":\"Tine, Q-Meieriene\",\"nutriments\":{\"energy-kcal_100g\":60}}}");

        OpenFoodFactsProduct p = OpenFoodFactsParser.parse(node).orElseThrow();

        assertEquals("Tine", p.brand());
        assertEquals(120, p.name().length());
    }

    @Test
    void ignoresAbsurdServingSizes() throws Exception {
        assertNull(parse("{\"status\":1,\"product\":{\"product_name\":\"X\",\"serving_quantity\":0,\"nutriments\":{\"energy-kcal_100g\":100}}}").orElseThrow().portionGrams());
        assertNull(parse("{\"status\":1,\"product\":{\"product_name\":\"X\",\"serving_quantity\":99999,\"nutriments\":{\"energy-kcal_100g\":100}}}").orElseThrow().portionGrams());
    }
}
