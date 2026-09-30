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
}
