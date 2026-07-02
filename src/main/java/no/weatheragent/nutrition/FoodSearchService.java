package no.weatheragent.nutrition;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Søk i den cachede Matvaretabellen. Rangeringen er enkel og forutsigbar:
 * navn som STARTER med søket først, deretter navn som inneholder det,
 * til slutt treff kun i søkeordene. Alfabetisk innen hver gruppe.
 */
@Service
public class FoodSearchService {

    private static final int MAX_RESULTS = 20;

    private final MatvaretabellenClient client;

    public FoodSearchService(MatvaretabellenClient client) {
        this.client = client;
    }

    public List<FoodItem> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return List.of();
        }

        return client.allFoods().stream()
                .filter(f -> rank(f, q) < Integer.MAX_VALUE)
                .sorted(Comparator
                        .comparingInt((FoodItem f) -> rank(f, q))
                        .thenComparing(f -> f.name().toLowerCase(Locale.ROOT)))
                .limit(MAX_RESULTS)
                .toList();
    }

    private static int rank(FoodItem food, String q) {
        String name = food.name().toLowerCase(Locale.ROOT);
        if (name.startsWith(q)) {
            return 0;
        }
        if (name.contains(q)) {
            return 1;
        }
        boolean keywordHit = food.searchKeywords().stream()
                .anyMatch(k -> k.toLowerCase(Locale.ROOT).contains(q));
        return keywordHit ? 2 : Integer.MAX_VALUE;
    }
}
