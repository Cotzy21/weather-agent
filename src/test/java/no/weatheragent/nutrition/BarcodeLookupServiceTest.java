package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BarcodeLookupServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final String CODE = "7622210449283";

    private final CustomFoodService customFoods = mock(CustomFoodService.class);
    private final OpenFoodFactsClient off = mock(OpenFoodFactsClient.class);
    private final BarcodeLookupService service = new BarcodeLookupService(customFoods, off, true);

    private static CustomFood food(boolean isPublic) {
        return new CustomFood(ME, "Prince", "Mondelez", CODE, 467, 6.3, 17, 69, "porsjon", 20.0, isPublic);
    }

    private static final OpenFoodFactsProduct PRODUCT = new OpenFoodFactsProduct("Prince", "Mondelez", 467, 6.3, 17, 69, 20.0);

    @Test
    void knownFoodsWinAndNoExternalCallIsMade() {
        CustomFood known = food(false);
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.of(known));

        BarcodeLookupService.Result result = service.lookup(ME, CODE).orElseThrow();

        assertEquals(known, result.food());
        assertFalse(result.importedFromOpenFoodFacts());
        verifyNoInteractions(off);
    }

    @Test
    void anOpenFoodFactsHitIsSavedAsAPrivateOwnFoodWithTheBarcode() {
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.empty());
        when(off.lookup(CODE)).thenReturn(Optional.of(PRODUCT));
        CustomFood saved = food(false);
        when(customFoods.create(any(), anyString(), any(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), eq(false))).thenReturn(saved);

        BarcodeLookupService.Result result = service.lookup(ME, CODE).orElseThrow();

        assertTrue(result.importedFromOpenFoodFacts());
        assertEquals(saved, result.food());
        // privat (false), med strekkoden, og porsjonen «porsjon» = 20 g
        verify(customFoods).create(ME, "Prince", "Mondelez", CODE, 467, 6.3, 17, 69, "porsjon", 20.0, false);
    }

    @Test
    void productsWithoutAServingSizeGetNoPortion() {
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.empty());
        when(off.lookup(CODE)).thenReturn(Optional.of(new OpenFoodFactsProduct("Melk", null, 46, 3.4, 1.5, 4.8, null)));
        when(customFoods.create(any(), anyString(), any(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), eq(false))).thenReturn(food(false));

        service.lookup(ME, CODE);

        verify(customFoods).create(ME, "Melk", null, CODE, 46, 3.4, 1.5, 4.8, null, null, false);
    }

    @Test
    void unknownEverywhereGivesNothing() {
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.empty());
        when(off.lookup(CODE)).thenReturn(Optional.empty());

        assertTrue(service.lookup(ME, CODE).isEmpty());
        verify(customFoods, never()).create(any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), eq(false));
    }

    @Test
    void anOutageAtOpenFoodFactsIsJustNotFoundNeverAnError() {
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.empty());
        when(off.lookup(CODE)).thenThrow(new OpenFoodFactsClient.UnavailableException("nede", null));

        assertTrue(service.lookup(ME, CODE).isEmpty());
    }

    @Test
    void badBarcodesNeverReachTheDatabaseOrTheExternalService() {
        for (String bad : new String[]{null, "", "abc", "123", "1234567890123456", "7622210449x83"}) {
            assertTrue(service.lookup(ME, bad).isEmpty(), String.valueOf(bad));
        }
        verifyNoInteractions(customFoods, off);
    }

    @Test
    void theExternalLookupCanBeSwitchedOff() {
        BarcodeLookupService off = new BarcodeLookupService(customFoods, this.off, false);
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.empty());

        assertTrue(off.lookup(ME, CODE).isEmpty());
        verifyNoInteractions(this.off);
    }

    @Test
    void theOwnFoodLimitStillAppliesAndTellsTheUser() {
        when(customFoods.byBarcode(ME, CODE)).thenReturn(Optional.empty());
        when(off.lookup(CODE)).thenReturn(Optional.of(PRODUCT));
        when(customFoods.create(any(), anyString(), any(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), eq(false))).thenThrow(new IllegalArgumentException("Du har allerede 200 egne matvarer - slett noen først."));

        assertThrows(IllegalArgumentException.class, () -> service.lookup(ME, CODE));
    }
}
