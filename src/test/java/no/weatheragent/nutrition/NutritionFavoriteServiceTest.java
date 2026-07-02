package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionFavoriteServiceTest {

    private final NutritionFavoriteRepository repo = mock(NutritionFavoriteRepository.class);
    private final NutritionFavoriteService service = new NutritionFavoriteService(repo);
    private final UUID user = UUID.randomUUID();

    @Test
    void addSavesNewFavorite() {
        when(repo.findByUserIdAndFoodKey(user, "gronnsaker:Brokkoli")).thenReturn(Optional.empty());
        when(repo.countByUserId(user)).thenReturn(3L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NutritionFavorite f = service.add(user, "gronnsaker:Brokkoli", "Brokkoli", "🥦", "vitaminer");

        assertEquals("gronnsaker:Brokkoli", f.getFoodKey());
        assertEquals("Brokkoli", f.getName());
        assertEquals(user, f.getUserId());
    }

    @Test
    void addIsIdempotentWhenFavoriteExists() {
        NutritionFavorite existing = new NutritionFavorite(user, "kjott:Egg", "Egg", "🥚", "protein");
        when(repo.findByUserIdAndFoodKey(user, "kjott:Egg")).thenReturn(Optional.of(existing));

        NutritionFavorite f = service.add(user, "kjott:Egg", "Egg", "🥚", "protein");

        assertEquals(existing, f);
        verify(repo, never()).save(any());
    }

    @Test
    void addRejectsWhenLimitReached() {
        when(repo.findByUserIdAndFoodKey(any(), any())).thenReturn(Optional.empty());
        when(repo.countByUserId(user)).thenReturn((long) NutritionFavoriteService.MAX_FAVORITES);

        assertThrows(FavoriteLimitException.class,
                () -> service.add(user, "frukt:Eple", "Eple", "🍎", "vitaminer"));
        verify(repo, never()).save(any());
    }

    @Test
    void removeReportsWhetherSomethingWasDeleted() {
        when(repo.deleteByUserIdAndFoodKey(user, "frukt:Eple")).thenReturn(1L);

        assertTrue(service.remove(user, "frukt:Eple"));
    }
}
