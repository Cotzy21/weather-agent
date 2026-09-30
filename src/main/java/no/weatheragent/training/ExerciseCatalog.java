package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Øvelseskatalogen med faste ID-er og navn på norsk og engelsk (ligger i {@code exercises.json},
 * delt med frontenden). «Bicep Curls» og «Bicepscurl» er samme øvelse, så progresjon, historikk og
 * minne grupperer på {@link #groupKey(String)}: katalog-ID når navnet gjenkjennes, ellers navnet
 * normalisert. Katalogen sier også hvilket utstyr en øvelse krever, så planer kan sjekkes mot
 * brukerens utstyr.
 */
public final class ExerciseCatalog {

    /** Én katalogøvelse. {@code requires} er utstyrstagger (barbell, dumbbell, cable, machine ...). */
    public record Entry(String id, String group, String nb, String en, Set<String> requires, boolean heavyLower) {
        public String name(boolean english) {
            return english ? en : nb;
        }
    }

    /** Utstyr en bruker har, per profilens {@code equipment}. Tomt sett = bare kroppsvekt. */
    private static final Map<String, Set<String>> EQUIPMENT = Map.of(
            "GYM", Set.of("barbell", "dumbbell", "cable", "machine", "bench", "rack", "pull-up-bar", "dip-bars"),
            "HOME_WEIGHTS", Set.of("dumbbell", "bench"),
            "BODYWEIGHT", Set.of());

    private static final ExerciseCatalog DEFAULT = load();

    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final Map<String, Entry> byKey = new HashMap<>();

    private ExerciseCatalog(JsonNode root) {
        for (JsonNode n : root.path("exercises")) {
            Set<String> req = new java.util.LinkedHashSet<>();
            n.path("requires").forEach(r -> req.add(r.asText()));
            Entry e = new Entry(n.path("id").asText(), n.path("group").asText(), n.path("nb").asText(),
                    n.path("en").asText(), Set.copyOf(req), n.path("heavyLower").asBoolean(false));
            byId.put(e.id(), e);
            index(e.nb(), e);
            index(e.en(), e);
            n.path("aliases").forEach(a -> index(a.asText(), e));
        }
    }

    public static ExerciseCatalog get() {
        return DEFAULT;
    }

    private static ExerciseCatalog load() {
        try (InputStream in = ExerciseCatalog.class.getResourceAsStream("/exercises.json")) {
            return new ExerciseCatalog(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void index(String name, Entry e) {
        byKey.putIfAbsent(normalize(name), e);
    }

    /** Små bokstaver, uten aksenter, mellomrom, bindestrek og tegn, og uten flertalls-«s» til slutt. */
    static String normalize(String name) {
        String s = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        // NFKD splitter æøå ikke opp (ø, æ er egne bokstaver), men å -> a; det er greit for oppslag.
        return s.length() > 3 && s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
    }

    public Optional<Entry> resolve(String name) {
        return Optional.ofNullable(byKey.get(normalize(name)));
    }

    public Optional<Entry> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Entry> all() {
        return new ArrayList<>(byId.values());
    }

    /** Nøkkel å gruppere på: katalog-ID, ellers navnet normalisert. Tom streng for tomt navn. */
    public static String groupKey(String name) {
        return DEFAULT.resolve(name).map(Entry::id).orElseGet(() -> normalize(name));
    }

    /** Krever øvelsen tung beintrening (knebøy, markløft ...)? Kjenner igjen begge språk. */
    public static boolean isHeavyLower(String name) {
        return DEFAULT.resolve(name).map(Entry::heavyLower).orElse(false);
    }

    /** Utstyr brukeren har for en profil-verdi; ukjent verdi behandles som treningssenter (ingen sperre). */
    public static Set<String> equipmentFor(String profileEquipment) {
        return EQUIPMENT.getOrDefault(profileEquipment == null ? "" : profileEquipment, EQUIPMENT.get("GYM"));
    }

    /** Mangler brukeren utstyr til denne øvelsen? Ukjente øvelser slippes gjennom. */
    public static boolean missingEquipment(String name, Set<String> have) {
        return DEFAULT.resolve(name).map(e -> !have.containsAll(e.requires())).orElse(false);
    }

    /**
     * Legger {@code exerciseId} på alle gjenkjente øvelsesblokker i en styrkeøkts innhold (også
     * øvelser i supersett). Idempotent; øvelser uten treff røres ikke.
     */
    public static JsonNode annotate(JsonNode content) {
        if (content == null) return content;
        for (JsonNode block : content.path("blocks")) {
            if ("superset".equals(block.path("kind").asText(""))) {
                block.path("exercises").forEach(ExerciseCatalog::tag);
            } else {
                tag(block);
            }
        }
        return content;
    }

    private static void tag(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            DEFAULT.resolve(node.path("name").asText("")).ifPresent(e -> obj.put("exerciseId", e.id()));
        }
    }
}
