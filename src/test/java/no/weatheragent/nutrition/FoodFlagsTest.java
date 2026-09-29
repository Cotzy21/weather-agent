package no.weatheragent.nutrition;

import no.weatheragent.nutrition.FoodFlags.Allergen;
import no.weatheragent.nutrition.FoodFlags.Diet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodFlagsTest {

    @Test
    void glutenIsFoundInCompoundsButNotInMilk() {
        assertTrue(Allergen.GLUTEN.matches("Brød, grovt, kjøpt"));
        assertTrue(Allergen.GLUTEN.matches("Hvetemel"));
        assertTrue(Allergen.GLUTEN.matches("Havregryn"));
        assertTrue(Allergen.GLUTEN.matches("Seitan"));
        assertFalse(Allergen.GLUTEN.matches("Melk, lett"));          // «mel» må ikke treffe «melk»
        assertFalse(Allergen.GLUTEN.matches("Glutenfritt brød"));     // fri-for-merke
        assertFalse(Allergen.GLUTEN.matches("Rispasta, kokt"));
        assertFalse(Allergen.GLUTEN.matches("Ris, kokt"));
    }

    @Test
    void milkHandlesCheeseAndPlantDrinksCorrectly() {
        assertTrue(Allergen.MELK.matches("Brunost"));
        assertTrue(Allergen.MELK.matches("Gresk yoghurt"));
        assertTrue(Allergen.MELK.matches("Melk, laktosefri"));       // laktosefri ≠ melkefri!
        assertFalse(Allergen.MELK.matches("Rostbiff"));              // «ost» midt i et ord
        assertFalse(Allergen.MELK.matches("Kokosmelk"));
        assertFalse(Allergen.MELK.matches("Havremelk"));
        assertFalse(Allergen.MELK.matches("Peanøttsmør"));
    }

    @Test
    void nutsAndPeanutsAreSeparateButMixedProductsHitBoth() {
        String mix = "Nøtteblanding med peanøtter";
        assertTrue(Allergen.NOTTER.matches(mix));
        assertTrue(Allergen.PEANOTTER.matches(mix));
        assertFalse(Allergen.NOTTER.matches("Peanøttsmør"));
        assertFalse(Allergen.NOTTER.matches("Kokosnøtt, tørket"));
        assertTrue(Allergen.NOTTER.matches("Mandler"));
    }

    @Test
    void knownFalseFriendsAreNotFlagged() {
        assertFalse(Allergen.FISK.matches("Seigmenn"));
        assertFalse(Allergen.FISK.matches("Tranebær, tørket"));
        assertFalse(Allergen.FISK.matches("Seitan"));
        assertFalse(Allergen.EGG.matches("Svinelegg"));
        assertTrue(Allergen.FISK.matches("Laks, oppdrett, rå"));
        assertTrue(Allergen.FISK.matches("Tran"));
    }

    @Test
    void dietRulesFollowTheDietDefinitions() {
        assertTrue(FoodFlags.violatesDiet("kyllingfilet", Diet.VEGETAR));
        assertFalse(FoodFlags.violatesDiet("vegetarburger", Diet.VEGETAR));
        assertTrue(FoodFlags.violatesDiet("laks", Diet.VEGETAR));
        assertFalse(FoodFlags.violatesDiet("laks", Diet.PESCETAR));
        assertTrue(FoodFlags.violatesDiet("kyllingfilet", Diet.PESCETAR));
        assertTrue(FoodFlags.violatesDiet("melk, hel", Diet.VEGAN));
        assertTrue(FoodFlags.violatesDiet("honning", Diet.VEGAN));
        assertFalse(FoodFlags.violatesDiet("havremelk", Diet.VEGAN));
        assertFalse(FoodFlags.violatesDiet("kyllingfilet", Diet.ALT));
    }

    @Test
    void warningsCombineAllergyDietAndDislikesInStableOrder() {
        DietProfile profile = new DietProfile(Diet.VEGAN, Set.of(Allergen.MELK, Allergen.GLUTEN), List.of("sopp"));

        assertEquals(List.of("ALLERGEN:GLUTEN", "ALLERGEN:MELK", "DIETT"),
                FoodFlags.warnings("Ostesmørbrød", profile));
        assertEquals(List.of("MISLIKER"), FoodFlags.warnings("Soppstuing", profile));
        assertTrue(FoodFlags.allowed("Epler", profile));
    }

    @Test
    void noProfileMeansNoWarnings() {
        assertTrue(FoodFlags.warnings("Rekesmørbrød med egg", DietProfile.NONE).isEmpty());
    }
}
