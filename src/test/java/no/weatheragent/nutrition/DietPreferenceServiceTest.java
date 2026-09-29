package no.weatheragent.nutrition;

import no.weatheragent.nutrition.FoodFlags.Allergen;
import no.weatheragent.nutrition.FoodFlags.Diet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DietPreferenceServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final DietPreferenceRepository repository = mock(DietPreferenceRepository.class);
    private final DietPreferenceService service = new DietPreferenceService(repository);

    @Test
    void noSavedPreferencesMeansNone() {
        when(repository.findById(USER)).thenReturn(Optional.empty());

        assertSame(DietProfile.NONE, service.profileFor(USER));
    }

    @Test
    void savesNormalizedProfileThatRoundTripsThroughTheEntity() {
        DietProfile saved = service.save(USER, "vegetar", List.of("melk", "GLUTEN"),
                List.of(" Sopp ", "sopp", "Rosenkål, rå"));

        assertEquals(Diet.VEGETAR, saved.diet());
        assertEquals(Set.of(Allergen.MELK, Allergen.GLUTEN), saved.allergies());
        assertEquals(List.of("sopp", "rosenkål rå"), saved.dislikes()); // komma er skilletegn i lagringen

        ArgumentCaptor<DietPreference> captor = ArgumentCaptor.forClass(DietPreference.class);
        verify(repository).save(captor.capture());
        assertEquals(saved, captor.getValue().toProfile());
    }

    @Test
    void unknownValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.save(USER, "keto", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.save(USER, "ALT", List.of("pollen"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.save(USER, "ALT", List.of(),
                Collections.nCopies(DietPreferenceService.MAX_DISLIKES + 1, "x").stream()
                        .map(s -> s + Math.random()).toList()));
    }
}
