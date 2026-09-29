package no.weatheragent.nutrition;

import no.weatheragent.nutrition.FoodFlags.Allergen;
import no.weatheragent.nutrition.FoodFlags.Diet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rådene skal aldri anbefale kilder brukeren ikke tåler eller vil spise. */
class NutrientReferenceTest {

    private static NutrientReference ref(String id) {
        return NutrientReference.TRACKED.stream().filter(r -> r.nutrientId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void defaultAdviceListsTheUsualSources() {
        String advice = ref("Ca").lowAdvice();

        assertTrue(advice.startsWith("Lite kalsium"));
        assertTrue(advice.contains("melk"));
    }

    @Test
    void milkAllergyGetsNonDairyCalciumSources() {
        String advice = ref("Ca").lowAdvice(new DietProfile(Diet.ALT, Set.of(Allergen.MELK), List.of()));

        assertFalse(advice.contains("melk,"));
        assertFalse(advice.contains("ost"));
        assertFalse(advice.contains("yoghurt"));
        assertTrue(advice.contains("grønnkål"));
        assertTrue(advice.contains("kalsiumberiket plantedrikk"));
    }

    @Test
    void veganGetsFortifiedOrSupplementB12() {
        String advice = ref("Vit B12").lowAdvice(new DietProfile(Diet.VEGAN, Set.of(), List.of()));

        assertFalse(advice.contains("kjøtt"));
        assertFalse(advice.contains("egg"));
        assertTrue(advice.contains("B12-beriket plantedrikk"));
        assertTrue(advice.contains("B12-tilskudd"));
    }

    @Test
    void fishAllergyGetsVitaminDWithoutFishOrCodLiverOil() {
        String advice = ref("Vit D").lowAdvice(new DietProfile(Diet.ALT, Set.of(Allergen.FISK), List.of()));

        assertFalse(advice.contains("fisk"));
        assertFalse(advice.contains("tran"));
        assertTrue(advice.contains("D-vitamintilskudd"));
    }

    @Test
    void whenNothingFitsItPointsToProfessionalsInstead() {
        List<String> dislikeEverything = ref("Vit C").sources();

        String advice = ref("Vit C").lowAdvice(new DietProfile(Diet.ALT, Set.of(), dislikeEverything));

        assertFalse(advice.contains("Gode kilder"));
        assertTrue(advice.contains("ernæringsfysiolog"));
    }
}
