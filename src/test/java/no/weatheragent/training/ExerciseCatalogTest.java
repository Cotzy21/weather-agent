package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExerciseCatalogTest {

    @Test
    void norwegianEnglishAndAliasNamesShareOneId() {
        String id = ExerciseCatalog.groupKey("Bicepscurl");
        assertEquals("bicep-curls", id);
        assertEquals(id, ExerciseCatalog.groupKey("Bicep Curls"));
        assertEquals(id, ExerciseCatalog.groupKey("  biceps curl "));
        assertEquals("squat", ExerciseCatalog.groupKey("Knebøy"));
        assertEquals("squat", ExerciseCatalog.groupKey("Squat"));
    }

    @Test
    void unknownNamesGroupOnNormalisedName() {
        assertEquals(ExerciseCatalog.groupKey("Min Øvelse"), ExerciseCatalog.groupKey("min  øvelse"));
        assertNotEquals(ExerciseCatalog.groupKey("Min øvelse"), ExerciseCatalog.groupKey("Din øvelse"));
    }

    @Test
    void everyNameInTheCatalogResolvesToItself() {
        for (var e : ExerciseCatalog.get().all()) {
            assertEquals(e.id(), ExerciseCatalog.groupKey(e.nb()), e.nb());
            assertEquals(e.id(), ExerciseCatalog.groupKey(e.en()), e.en());
        }
    }

    @Test
    void annotateAddsIdsToBlocksAndSupersetsIdempotently() throws Exception {
        var content = new ObjectMapper().readTree("""
                {"blocks":[{"kind":"exercise","name":"Bicepscurl","sets":[]},
                {"kind":"superset","exercises":[{"name":"Knebøy","sets":[]},{"name":"Min egen","sets":[]}]}]}""");
        ExerciseCatalog.annotate(content);
        ExerciseCatalog.annotate(content);
        assertEquals("bicep-curls", content.path("blocks").get(0).path("exerciseId").asText());
        assertEquals("squat", content.path("blocks").get(1).path("exercises").get(0).path("exerciseId").asText());
        assertTrue(content.path("blocks").get(1).path("exercises").get(1).path("exerciseId").isMissingNode());
    }

    @Test
    void equipmentRulesFollowTheProfile() {
        var home = ExerciseCatalog.equipmentFor("HOME_WEIGHTS");
        assertTrue(ExerciseCatalog.missingEquipment("Face Pulls", home));
        assertTrue(!ExerciseCatalog.missingEquipment("Bicep Curls", home));
        assertTrue(ExerciseCatalog.missingEquipment("Bicep Curls", ExerciseCatalog.equipmentFor("BODYWEIGHT")));
        assertTrue(!ExerciseCatalog.missingEquipment("Face Pulls", ExerciseCatalog.equipmentFor("GYM")));
    }

    @Test
    void noNameOrAliasBelongsToTwoDifferentExercises() {
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        for (var e : ExerciseCatalog.get().all()) {
            seen.putIfAbsent(ExerciseCatalog.normalize(e.nb()), e.id());
            seen.putIfAbsent(ExerciseCatalog.normalize(e.en()), e.id());
        }
        // Alle navn slår opp til øvelsen de står under (fanger at et alias «stjeles» av en annen øvelse).
        for (var e : ExerciseCatalog.get().all()) {
            assertEquals(e.id(), ExerciseCatalog.get().resolve(e.nb()).orElseThrow().id(), e.nb());
            assertEquals(e.id(), ExerciseCatalog.get().resolve(e.en()).orElseThrow().id(), e.en());
        }
    }

    @Test
    void garminExerciseNamesJoinTheAppsOwnExercises() {
        // Navnene Garmin-klokka skriver i FIT-filer skal samles med det du logger selv i appen.
        assertEquals("bench-press", ExerciseCatalog.groupKey("Barbell Bench Press"));
        assertEquals("bench-press", ExerciseCatalog.groupKey("Benkpress"));
        assertEquals("squat", ExerciseCatalog.groupKey("Barbell Back Squat"));
        assertEquals("deadlift", ExerciseCatalog.groupKey("Barbell Deadlift"));
        assertEquals("lat-pulldown", ExerciseCatalog.groupKey("Lat Pulldown"));
        assertEquals("bicep-curls", ExerciseCatalog.groupKey("Standing Dumbbell Biceps Curl"));
        assertEquals("leg-press", ExerciseCatalog.groupKey("Leg Press"));
        assertEquals("overhead-press", ExerciseCatalog.groupKey("Barbell Shoulder Press"));
        // manual- og stangvarianter er ulike øvelser (helt ulike vekter), og skal ikke blandes
        assertEquals("dumbbell-shoulder-press", ExerciseCatalog.groupKey("Seated Dumbbell Shoulder Press"));
        assertEquals("barbell-curl", ExerciseCatalog.groupKey("Barbell Biceps Curl"));
    }
}
