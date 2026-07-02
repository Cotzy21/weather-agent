package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoodSearchServiceTest {

    private static FoodItem food(String id, String name, String... keywords) {
        return new FoodItem(id, name, 100, Map.of(), List.of(), List.of(keywords));
    }

    private final MatvaretabellenClient client = mock(MatvaretabellenClient.class);
    private final FoodSearchService search = new FoodSearchService(client);

    @Test
    void ranksStartsWithBeforeContainsBeforeKeyword() {
        when(client.allFoods()).thenReturn(List.of(
                food("1", "Grovbrød med melk"),          // inneholder "melk"
                food("2", "Melk, lett"),                  // starter med "melk"
                food("3", "Hvit saus", "melk", "saus"))); // bare søkeord

        List<FoodItem> hits = search.search("melk");

        assertEquals(List.of("2", "1", "3"), hits.stream().map(FoodItem::foodId).toList());
    }

    @Test
    void emptyQueryGivesNoHitsWithoutTouchingTheTable() {
        assertTrue(search.search("   ").isEmpty());
    }

    @Test
    void searchIsCaseInsensitive() {
        when(client.allFoods()).thenReturn(List.of(food("1", "Brød, fint")));

        assertEquals(1, search.search("BRØD").size());
    }
}
