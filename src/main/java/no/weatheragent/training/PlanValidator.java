package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sjekker en AI-plan mot det brukeren faktisk har bedt om og har (regelbasert, ingen LLM):
 * utstyr, øvelser de ikke liker, tung beintrening samme dag som/dagen før kampsport, og styrke på
 * kampsportdager når brukeren ba om det. Funn brukes til ett retry mot modellen, og det som kan
 * repareres deterministisk (øvelser) repareres i {@link #repair}.
 */
public final class PlanValidator {

    /** Hva planen skal sjekkes mot. {@code equipment} er null når brukeren ikke er onboardet. */
    public record Context(String equipment, List<String> disliked, String requestText) {
        public Context {
            disliked = disliked == null ? List.of() : disliked;
            requestText = requestText == null ? "" : requestText;
        }
    }

    /** Ett brudd med tekst til modellen (norsk) og til brukeren (engelsk). {@code repairable}: kan fjernes automatisk. */
    public record Violation(String message, String messageEn, boolean repairable) {
    }

    private static final Map<String, Integer> DAYS = Map.ofEntries(
            Map.entry("mandag", 0), Map.entry("monday", 0), Map.entry("tirsdag", 1), Map.entry("tuesday", 1),
            Map.entry("onsdag", 2), Map.entry("wednesday", 2), Map.entry("torsdag", 3), Map.entry("thursday", 3),
            Map.entry("fredag", 4), Map.entry("friday", 4), Map.entry("lørdag", 5), Map.entry("saturday", 5),
            Map.entry("søndag", 6), Map.entry("sunday", 6));

    private static final String COMBAT = "bjj|jiu|kampsport|boksing|boxing|mma|judo|bryting|wrestling|muay|thai|karate|taekwondo";
    /** «styrke på BJJ-dagene», «lifting on BJJ days» og omvendt. */
    private static final Pattern WANTS_STRENGTH_ON_COMBAT_DAYS = Pattern.compile(
            "(styrke|strength|lift|løft|vekt).{0,50}(" + COMBAT + ")|(" + COMBAT + ").{0,50}(styrke|strength|lift|løft|vekt)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private PlanValidator() {
    }

    public static List<Violation> check(PlanSuggestion plan, Context ctx) {
        List<Violation> out = new ArrayList<>();
        if (plan == null) return out;

        EquipmentNotes.Result equipment = effectiveEquipment(ctx);
        Set<String> have = equipment.have();
        String haveSource = equipment.fromText() ? "ut fra det brukeren skrev om utstyr" : ctx.equipment();
        Set<String> dislikedKeys = new HashSet<>();
        ctx.disliked().forEach(d -> dislikedKeys.add(ExerciseCatalog.groupKey(d)));

        Set<String> reportedEquipment = new HashSet<>();
        Set<String> reportedDisliked = new HashSet<>();
        for (Suggestion w : plan.workouts()) {
            for (String name : exerciseNames(w)) {
                String key = ExerciseCatalog.groupKey(name);
                if (have != null && ExerciseCatalog.missingEquipment(name, have) && reportedEquipment.add(key)) {
                    out.add(new Violation(
                            "«" + name + "» krever utstyr brukeren ikke har (" + haveSource + "). Fjern eller bytt den.",
                            "\"" + name + "\" needs equipment you don't have.", true));
                }
                if (dislikedKeys.contains(key) && reportedDisliked.add(key)) {
                    out.add(new Violation("«" + name + "» står under «Liker IKKE». Fjern den.",
                            "\"" + name + "\" is on your dislike list.", true));
                }
            }
        }

        // Kampsportdager (0-6) og styrkedager med tung bein.
        Set<Integer> combatDays = new HashSet<>();
        Set<Integer> strengthDays = new HashSet<>();
        for (Suggestion w : plan.workouts()) {
            int day = dayOf(w);
            if (day < 0) continue;
            if ("KAMPSPORT".equals(w.type())) combatDays.add(day);
            if ("STYRKE".equals(w.type())) strengthDays.add(day);
        }
        for (Suggestion w : plan.workouts()) {
            int day = dayOf(w);
            if (day < 0 || !"STYRKE".equals(w.type()) || !hasHeavyLower(w)) continue;
            if (combatDays.contains(day) || combatDays.contains((day + 1) % 7)) {
                out.add(new Violation(
                        "«" + w.title() + "» har tung beintrening samme dag som eller dagen før kampsport. Bruk overkropp eller lett økt.",
                        "\"" + w.title() + "\" has heavy leg work on or right before a combat-sport day.", false));
            }
        }
        if (WANTS_STRENGTH_ON_COMBAT_DAYS.matcher(ctx.requestText()).find()) {
            for (int day : combatDays) {
                if (!strengthDays.contains(day)) {
                    out.add(new Violation(
                            "Brukeren ba om styrketrening på kampsportdagene, men det mangler en styrkeøkt på " + dayName(day)
                                    + ". Legg til en (overkropp eller lett).",
                            "You asked for strength work on combat-sport days, but " + dayName(day) + " has none.", false));
                }
            }
        }
        return out;
    }

    /**
     * Utstyret planen sjekkes mot: profilens sett, men fritekst i forespørselen («har bare kettlebells», «ingen stang»)
     * går foran, siden det er det brukeren ber om akkurat nå. Null = ingen begrensning (ikke onboardet, ingen begrensende tekst).
     */
    static EquipmentNotes.Result effectiveEquipment(Context ctx) {
        Set<String> profile = ctx.equipment() == null ? null : ExerciseCatalog.equipmentFor(ctx.equipment());
        return EquipmentNotes.apply(ctx.requestText(), profile);
    }

    /** Fjerner øvelser som bryter utstyr/liker-ikke fra styrkeøktene. Øktene beholdes selv om de blir tomme. */
    public static PlanSuggestion repair(PlanSuggestion plan, Context ctx) {
        Set<String> have = effectiveEquipment(ctx).have();
        Set<String> dislikedKeys = new HashSet<>();
        ctx.disliked().forEach(d -> dislikedKeys.add(ExerciseCatalog.groupKey(d)));
        List<Suggestion> fixed = new ArrayList<>();
        for (Suggestion w : plan.workouts()) {
            JsonNode content = w.content();
            if ("STYRKE".equals(w.type()) && content instanceof ObjectNode obj && content.path("blocks").isArray()) {
                ArrayNode kept = obj.arrayNode();
                for (JsonNode b : content.path("blocks")) {
                    if ("superset".equals(b.path("kind").asText(""))) {
                        if (b instanceof ObjectNode sup && b.path("exercises").isArray()) {
                            ArrayNode ex = sup.arrayNode();
                            b.path("exercises").forEach(e -> {
                                if (!bad(e.path("name").asText(""), have, dislikedKeys)) ex.add(e);
                            });
                            if (ex.size() >= 2) {
                                sup.set("exercises", ex);
                                kept.add(sup);
                            } else if (ex.size() == 1) { // en øvelse igjen: gjør den om til en vanlig blokk
                                kept.add(ex.get(0));
                            }
                            continue;
                        }
                    }
                    if (!bad(b.path("name").asText(""), have, dislikedKeys)) kept.add(b);
                }
                ObjectNode copy = obj.deepCopy();
                copy.set("blocks", kept);
                content = copy;
            }
            fixed.add(new Suggestion(w.title(), w.type(), content, w.rationale()));
        }
        return new PlanSuggestion(plan.title(), plan.summary(), fixed);
    }

    private static boolean bad(String name, Set<String> have, Set<String> disliked) {
        return (have != null && ExerciseCatalog.missingEquipment(name, have))
                || disliked.contains(ExerciseCatalog.groupKey(name));
    }

    static List<String> exerciseNames(Suggestion w) {
        List<String> names = new ArrayList<>();
        if (!"STYRKE".equals(w.type())) return names;
        for (JsonNode b : w.content().path("blocks")) {
            if ("superset".equals(b.path("kind").asText(""))) {
                b.path("exercises").forEach(e -> names.add(e.path("name").asText("")));
            } else {
                names.add(b.path("name").asText(""));
            }
        }
        names.removeIf(String::isBlank);
        return names;
    }

    private static boolean hasHeavyLower(Suggestion w) {
        return exerciseNames(w).stream().anyMatch(ExerciseCatalog::isHeavyLower);
    }

    private static int dayOf(Suggestion w) {
        String d = w.content().path("day").asText("").trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : DAYS.entrySet()) {
            if (d.startsWith(e.getKey())) return e.getValue();
        }
        return -1;
    }

    private static String dayName(int i) {
        return new String[]{"mandag", "tirsdag", "onsdag", "torsdag", "fredag", "lørdag", "søndag"}[i];
    }

    /** Advarselstekst til planens sammendrag for brudd som ikke lot seg rette. */
    public static String warning(List<Violation> left, boolean english) {
        if (left.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(english ? "\n\n⚠ Check before starting: " : "\n\n⚠ Sjekk før du starter: ");
        for (int i = 0; i < left.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(english ? left.get(i).messageEn() : left.get(i).message());
        }
        return sb.toString();
    }
}
