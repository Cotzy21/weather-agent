package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Suggestion strength(String title, String day, String... exercises) {
        StringBuilder blocks = new StringBuilder();
        for (String e : exercises) {
            if (blocks.length() > 0) blocks.append(',');
            blocks.append("{\"kind\":\"exercise\",\"name\":\"").append(e).append("\",\"sets\":[{\"reps\":8,\"weightKg\":20}]}");
        }
        return new Suggestion(title, "STYRKE", json("{\"day\":\"" + day + "\",\"blocks\":[" + blocks + "]}"), "");
    }

    private static Suggestion combat(String day) {
        return new Suggestion("BJJ", "KAMPSPORT", json("{\"day\":\"" + day + "\",\"durationMin\":60}"), "");
    }

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static PlanSuggestion plan(Suggestion... w) {
        return new PlanSuggestion("Plan", "", List.of(w));
    }

    private static PlanValidator.Context ctx(String equipment, String request) {
        return new PlanValidator.Context(equipment, List.of(), request);
    }

    @Test
    void flagsFacePullsWhenUserOnlyHasDumbbells() {
        var v = PlanValidator.check(plan(strength("Pull", "mandag", "Face Pulls", "Bicepscurl")), ctx("HOME_WEIGHTS", ""));
        assertEquals(1, v.size());
        assertTrue(v.get(0).message().contains("Face Pulls"));
        assertTrue(v.get(0).repairable());
    }

    @Test
    void gymUsersAndUnknownExercisesAreNotFlagged() {
        assertTrue(PlanValidator.check(plan(strength("Pull", "mandag", "Face Pulls")), ctx("GYM", "")).isEmpty());
        assertTrue(PlanValidator.check(plan(strength("X", "mandag", "Helt egen øvelse")), ctx("BODYWEIGHT", "")).isEmpty());
        assertTrue(PlanValidator.check(plan(strength("Pull", "mandag", "Face Pulls")), ctx(null, "")).isEmpty());
    }

    @Test
    void flagsHeavyLegsDayBeforeAndSameDayAsCombat() {
        var before = PlanValidator.check(plan(strength("Bein", "mandag", "Knebøy"), combat("tirsdag")), ctx("GYM", ""));
        assertEquals(1, before.size());
        var same = PlanValidator.check(plan(strength("Bein", "tirsdag", "Deadlift"), combat("tuesday")), ctx("GYM", ""));
        assertEquals(1, same.size());
        var fine = PlanValidator.check(plan(strength("Bein", "onsdag", "Knebøy"), combat("tirsdag")), ctx("GYM", ""));
        assertTrue(fine.isEmpty());
    }

    @Test
    void sundayLegsBeforeMondayCombatWrapsAround() {
        var v = PlanValidator.check(plan(strength("Bein", "søndag", "Squat"), combat("mandag")), ctx("GYM", ""));
        assertEquals(1, v.size());
    }

    @Test
    void requiresStrengthOnCombatDaysWhenUserAskedForIt() {
        var plan = plan(strength("Push", "mandag", "Benkpress"), combat("tirsdag"));
        var asked = PlanValidator.check(plan, ctx("GYM", "Jeg trener BJJ tirsdag og vil ha styrke på BJJ-dagene"));
        assertEquals(1, asked.size());
        assertTrue(asked.get(0).message().contains("tirsdag"));
        assertTrue(PlanValidator.check(plan, ctx("GYM", "Jeg trener BJJ tirsdag")).isEmpty());
    }

    @Test
    void dislikedExercisesAreMatchedAcrossLanguages() {
        var ctx = new PlanValidator.Context("GYM", List.of("Bicepscurl"), "");
        var v = PlanValidator.check(plan(strength("Arms", "mandag", "Bicep Curls")), ctx);
        assertEquals(1, v.size());
    }

    @Test
    void repairRemovesOffendingExercisesAndKeepsTheRest() {
        var ctx = ctx("HOME_WEIGHTS", "");
        var fixed = PlanValidator.repair(plan(strength("Pull", "mandag", "Face Pulls", "Bicepscurl")), ctx);
        String content = fixed.workouts().get(0).content().toString();
        assertTrue(!content.contains("Face Pulls"));
        assertTrue(content.contains("Bicepscurl"));
        assertTrue(PlanValidator.check(fixed, ctx).isEmpty());
    }
}
