package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** De engelske tekstene må henge sammen med de norske, ellers vises feil kilde i feil språk. */
class NutrientTranslationsTest {

    @Test
    void everyTrackedNutrientHasAnEnglishTranslation() {
        for (NutrientReference ref : NutrientReference.TRACKED) {
            NutrientTranslations.En en = NutrientTranslations.of(ref.nutrientId());
            assertNotNull(en, "mangler oversettelse for " + ref.nutrientId());
            assertFalse(en.name().isBlank(), ref.nutrientId());
            assertFalse(en.consequence().isBlank(), ref.nutrientId());
        }
    }

    @Test
    void sourceListsHaveTheSameLengthSoTheAllergyFilterPicksTheRightEnglishWord() {
        for (NutrientReference ref : NutrientReference.TRACKED) {
            assertEquals(ref.sources().size(), NutrientTranslations.of(ref.nutrientId()).sources().size(),
                    "kildelistene for " + ref.nutrientId() + " må ha samme lengde og rekkefølge");
        }
    }

    @Test
    void noTranslationWithoutATrackedNutrient() {
        assertEquals(NutrientReference.TRACKED.size(), NutrientTranslations.BY_ID.size());
    }

    @Test
    void englishTextIsActuallyEnglishNotACopyOfTheNorwegian() {
        for (NutrientReference ref : NutrientReference.TRACKED) {
            assertTrue(!NutrientTranslations.of(ref.nutrientId()).consequence().equals(ref.consequence()), ref.nutrientId());
        }
    }
}
