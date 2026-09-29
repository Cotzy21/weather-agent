package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomFoodServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private final CustomFoodRepository repository = mock(CustomFoodRepository.class);
    private final CustomFoodService service = new CustomFoodService(repository);

    private CustomFood create(String barcode, boolean isPublic) {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return service.create(ME, "  Proteinshake ", " ", barcode,
                380, 75, 5, 8, "scoop", 30.0, isPublic);
    }

    @Test
    void createsTrimmedFoodWithPortion() {
        CustomFood f = create(null, true);

        assertEquals("Proteinshake", f.getName());
        assertNull(f.getBrand()); // blankt merke -> null
        assertEquals(30.0, f.getPortionGrams());
        assertTrue(f.isPublic());
    }

    @Test
    void impossibleNutritionIsRejected() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Makroer på 120 g per 100 g er fysisk umulig.
        assertThrows(IllegalArgumentException.class, () -> service.create(ME, "Tull", null, null,
                400, 60, 30, 30, null, null, true));
        assertThrows(IllegalArgumentException.class, () -> service.create(ME, "Tull", null, null,
                1200, 10, 10, 10, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.create(ME, " ", null, null,
                100, 10, 10, 10, null, null, false));
    }

    @Test
    void barcodeIsNormalizedAndValidated() {
        assertEquals("7038010009457", create("7038 0100 0945 7", false).getBarcode());
        assertThrows(IllegalArgumentException.class, () -> create("abc123", false));
    }

    @Test
    void userLimitIsEnforced() {
        when(repository.countByOwnerId(ME)).thenReturn((long) CustomFoodService.MAX_PER_USER);

        assertThrows(IllegalArgumentException.class, () -> create(null, false));
    }

    @Test
    void usableOnlyForOwnerOrPublic() {
        UUID id = UUID.randomUUID();
        CustomFood othersPrivate = new CustomFood(OTHER, "Hemmelig", null, null, 100, 1, 1, 1, null, null, false);
        when(repository.findById(id)).thenReturn(Optional.of(othersPrivate));

        assertTrue(service.usable(ME, "egen:" + id).isEmpty());

        CustomFood othersPublic = new CustomFood(OTHER, "Delt", null, null, 100, 1, 1, 1, null, null, true);
        when(repository.findById(id)).thenReturn(Optional.of(othersPublic));

        assertTrue(service.usable(ME, "egen:" + id).isPresent());
    }

    @Test
    void malformedIdsAreNotUsable() {
        assertTrue(service.usable(ME, "05.049").isEmpty());
        assertTrue(service.usable(ME, "egen:ikke-en-uuid").isEmpty());
    }

    @Test
    void shortQueriesDoNotHitTheDatabase() {
        assertEquals(List.of(), service.search(ME, "a"));
    }

    @Test
    void toFoodItemIncludesBrandAndPortion() {
        CustomFood f = new CustomFood(ME, "Proteinshake", "Merke", null, 380, 75, 5, 8, "scoop", 30.0, false);

        FoodItem item = f.toFoodItem();

        assertEquals("Proteinshake (Merke)", item.name());
        assertEquals(75, item.nutrient("Protein"));
        assertEquals("scoop", item.portions().getFirst().name());
        assertTrue(item.foodId().startsWith(CustomFood.ID_PREFIX));
    }
}
