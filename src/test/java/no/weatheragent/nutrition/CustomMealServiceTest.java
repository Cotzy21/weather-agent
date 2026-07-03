package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomMealServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CustomMealRepository repo = mock(CustomMealRepository.class);
    private final CustomMealService service = new CustomMealService(repo);
    private final UUID user = UUID.randomUUID();

    private static com.fasterxml.jackson.databind.JsonNode json(String s) {
        try { return MAPPER.readTree(s); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test
    void savesMealWithIngredients() {
        when(repo.countByUserId(user)).thenReturn(2L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomMeal m = service.save(user, "  Treningsfrokost ",
                json("[{\"key\":\"korn:Havregryn\",\"grams\":80}]"));

        assertEquals("Treningsfrokost", m.getName());
        assertEquals(user, m.getUserId());
        assertEquals(80, m.getIngredients().get(0).path("grams").asInt());
    }

    @Test
    void rejectsEmptyOrMissingIngredients() {
        assertThrows(InvalidMealException.class, () -> service.save(user, "Tomt", json("[]")));
        assertThrows(InvalidMealException.class, () -> service.save(user, "Feil", json("{}")));
        assertThrows(InvalidMealException.class, () -> service.save(user, "Null", null));
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsTooManyIngredients() {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i <= CustomMealService.MAX_INGREDIENTS; i++) {
            arr.append(i > 0 ? "," : "").append("{\"grams\":10}");
        }
        arr.append("]");

        assertThrows(InvalidMealException.class, () -> service.save(user, "Gigant", json(arr.toString())));
    }

    @Test
    void rejectsWhenMealLimitReached() {
        when(repo.countByUserId(user)).thenReturn((long) CustomMealService.MAX_MEALS);

        assertThrows(MealLimitException.class,
                () -> service.save(user, "Enda et", json("[{\"grams\":10}]")));
        verify(repo, never()).save(any());
    }

    @Test
    void deleteReportsWhetherSomethingWasRemoved() {
        UUID id = UUID.randomUUID();
        when(repo.deleteByIdAndUserId(id, user)).thenReturn(1L);

        assertTrue(service.delete(id, user));
    }
}
