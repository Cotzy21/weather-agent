package no.weatheragent.training;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentNotesTest {

    private static final Set<String> GYM = ExerciseCatalog.equipmentFor("GYM");
    private static final Set<String> HOME = ExerciseCatalog.equipmentFor("HOME_WEIGHTS");

    private static Set<String> have(String text, Set<String> profile) {
        return EquipmentNotes.apply(text, profile).have();
    }

    private static boolean fromText(String text, Set<String> profile) {
        return EquipmentNotes.apply(text, profile).fromText();
    }

    @Test
    void onlyKettlebellsLeavesJustTheDumbbellLikeExercisesNoBenchNoBarbellNoCable() {
        assertEquals(Set.of("dumbbell"), have("Jeg har bare kettlebells hjemme", GYM));
        assertTrue(fromText("Jeg har bare kettlebells hjemme", GYM));
    }

    @Test
    void onlyDumbbellsMeansDumbbellsAndABenchLikeTheProfileHomeWeights() {
        assertEquals(Set.of("dumbbell", "bench"), have("bare manualer", GYM));
        assertEquals(Set.of("dumbbell", "bench"), have("I only have dumbbells", GYM));
        assertEquals(Set.of("dumbbell", "bench"), have("kun manualer og en benk", null));
    }

    @Test
    void noBenchOverridesTheBenchThatDumbbellsImply() {
        assertEquals(Set.of("dumbbell"), have("only dumbbells, no bench", GYM));
        assertEquals(Set.of("dumbbell"), have("bare manualer, ingen benk", GYM));
    }

    @Test
    void listsAfterOnlyAreReadUpToTheEndOfTheSentence() {
        assertEquals(Set.of("barbell", "rack"), have("I have just a barbell and a squat rack.", GYM));
        // «bare» i en setning uten utstyr, og utstyr i neste setning: ingen begrensning
        assertEquals(GYM, have("Jeg har bare lite tid. Manualer finnes på jobben.", GYM));
    }

    @Test
    void exclusionsRemoveJustThatEquipment() {
        assertEquals(minus(GYM, "cable"), have("ingen kabelmaskin", GYM));
        assertEquals(minus(GYM, "barbell", "bench"), have("uten stang og benk", GYM));
        assertEquals(minus(GYM, "barbell"), have("no barbell at my gym", GYM));
        assertEquals(minus(GYM, "machine"), have("Jeg har ikke tilgang på maskiner", GYM));
        // «cable machine» er kabel, ikke også maskin (leg press m.m. skal ikke falle bort)
        assertEquals(minus(GYM, "cable"), have("no cable machine at the gym", GYM));
        assertEquals(minus(GYM, "cable"), have("ingen kabelmaskin", GYM));
        assertEquals(minus(HOME, "bench"), have("I don't have a bench", HOME));
    }

    @Test
    void exclusionsWorkEvenWhenTheProfileIsMissing() {
        Set<String> have = have("no barbell", null);
        assertEquals(minus(GYM, "barbell"), have);
        assertTrue(fromText("no barbell", null));
    }

    @Test
    void noEquipmentMeansBodyweightOnly() {
        assertEquals(Set.of(), have("Jeg har ingen utstyr", GYM));
        assertEquals(Set.of(), have("I have no equipment", GYM));
        assertEquals(Set.of(), have("bare kroppsvekt", GYM));
        assertEquals(Set.of(), have("only bodyweight please", GYM));
    }

    @Test
    void theTextBeatsTheProfileBecauseItIsWhatTheUserAsksForNow() {
        assertEquals(Set.of("dumbbell", "bench"), have("Denne uka er jeg hjemme med bare manualer", GYM));
    }

    @Test
    void unrelatedOrVagueTextChangesNothing() {
        for (String text : new String[]{null, "", "  ", "Jeg vil trene 4 dager i uka",
                "jeg liker ikke maskiner", "bare kjør på!", "I just want more strength",
                "kunne vi lagt inn mer overkropp?", "barbell curl er favoritten min"}) {
            assertEquals(GYM, have(text, GYM), String.valueOf(text));
            assertFalse(fromText(text, GYM), String.valueOf(text));
        }
        assertNull(have("Jeg vil trene mer", null));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(Set.of("dumbbell", "bench"), have("BARE MANUALER", GYM));
    }

    private static Set<String> minus(Set<String> base, String... tags) {
        Set<String> out = new java.util.HashSet<>(base);
        for (String t : tags) out.remove(t);
        return out;
    }
}
