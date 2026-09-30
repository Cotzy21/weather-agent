package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Tolker svaret fra Open Food Facts ({@code /api/v2/product/<strekkode>}). Dataene er dugnadsbaserte og kan være
 * ufullstendige eller feil, så alt sjekkes med de samme grensene som egne matvarer i {@link CustomFoodService}:
 * ugyldige produkter gir {@code Optional.empty()} i stedet for rare tall i dagboka.
 */
final class OpenFoodFactsParser {

    private static final double KJ_PER_KCAL = 4.184;
    private static final int MAX_NAME = 120;
    private static final int MAX_BRAND = 80;

    private OpenFoodFactsParser() {
    }

    static Optional<OpenFoodFactsProduct> parse(JsonNode root) {
        if (root == null || root.path("status").asInt(0) != 1) {
            return Optional.empty();
        }
        JsonNode product = root.path("product");
        String name = firstNonBlank(text(product.path("product_name")), text(product.path("generic_name")));
        if (name == null) {
            return Optional.empty();
        }
        JsonNode n = product.path("nutriments");
        double kcal = number(n.path("energy-kcal_100g"));
        if (Double.isNaN(kcal)) {
            double kj = number(firstPresent(n.path("energy-kj_100g"), n.path("energy_100g")));
            kcal = Double.isNaN(kj) ? Double.NaN : Math.round(kj / KJ_PER_KCAL);
        }
        if (Double.isNaN(kcal) || kcal < 0 || kcal > 900) {
            return Optional.empty();
        }
        double protein = orZero(number(n.path("proteins_100g")));
        double fat = orZero(number(n.path("fat_100g")));
        double carb = orZero(number(n.path("carbohydrates_100g")));
        if (protein < 0 || fat < 0 || carb < 0 || protein + fat + carb > 100.5) {
            return Optional.empty();
        }
        double serving = number(product.path("serving_quantity"));
        Double portionGrams = !Double.isNaN(serving) && serving > 0 && serving <= 5000 ? serving : null;

        String brands = text(product.path("brands"));
        String brand = brands == null ? null : cut(brands.split(",")[0].trim(), MAX_BRAND);
        return Optional.of(new OpenFoodFactsProduct(cut(name, MAX_NAME), brand == null || brand.isEmpty() ? null : brand,
                kcal, protein, fat, carb, portionGrams));
    }

    private static JsonNode firstPresent(JsonNode a, JsonNode b) {
        return a.isMissingNode() || a.isNull() ? b : a;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String s = node.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /** Tall, også når Open Food Facts har sendt det som tekst. NaN hvis det mangler eller ikke er et tall. */
    private static double number(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Double.NaN;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static double orZero(double v) {
        return Double.isNaN(v) ? 0 : v;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
