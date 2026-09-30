package no.weatheragent.training;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leser utstyr ut av brukerens fritekst («jeg har bare kettlebells», «kun manualer og benk», «no cable machine»,
 * «ingen stang»), så utstyrssjekken i {@link PlanValidator} også fanger det profilen ikke vet om. Regelbasert og bevisst
 * forsiktig: bare klare formuleringer teller, alt annet lar profilen stå.
 *
 * <ul>
 *   <li><b>Begrensning</b> («bare/kun/only/just» + utstyr i samme setning): det brukeren nevner er ALT hun har.
 *       Manualer regnes med benk (som profilen «manualer hjemme»), med mindre benk er utelukket. Kettlebells regnes som
 *       manualer (slik katalogen gjør), men uten benk.</li>
 *   <li><b>Utelukkelse</b> («ingen/uten/no/without/har ikke tilgang på» + utstyr): dette utstyret fjernes.</li>
 *   <li>«Ingen utstyr» / «no equipment»: bare kroppsvekt.</li>
 * </ul>
 * Teksten går foran profilen fordi den er det brukeren ber om akkurat nå (hjemme denne uka, på reise ...).
 */
final class EquipmentNotes {

    /** Utstyret slik det står i {@code exercises.json} ({@code requires}); alt et treningssenter har. */
    private static final Set<String> CATALOG_TAGS = ExerciseCatalog.equipmentFor("GYM");

    /** Utfallet: utstyret brukeren regnes å ha, og om det kom fra teksten (ellers er det profilens sett uendret). */
    record Result(Set<String> have, boolean fromText) {
    }

    private record Item(String tag, Pattern pattern) {
    }

    private static Item item(String tag, String words) {
        return new Item(tag, Pattern.compile("(?<!\\p{L})(?:" + words + ")(?!\\p{L})"));
    }

    /** Ord på norsk og engelsk -> utstyrstagg. Tagger uten katalogøvelse (kettlebell, band) betyr «ikke noe av det øvrige». */
    private static final List<Item> ITEMS = List.of(
            item("dumbbell", "manual(?:er|ene|en)?|hantel(?:er|ene)?|dumbbells?"),
            item("barbell", "vektstang(?:a|en)?|langstang(?:a|en)?|stang(?:a|en)?|barbells?"),
            item("bench", "benk(?:en|er)?|bench(?:es)?"),
            item("cable", "kabel(?:maskin(?:en|er)?|trekk)?|cables?(?: machines?)?"),
            item("machine", "maskin(?:er|ene|en)?|machines?"),
            item("rack", "rack|stativ|knebøystativ|squat ?rack|power ?rack"),
            item("pull-up-bar", "chinsstang(?:a|en)?|pull-?up ?bar|chin-?up ?bar|hangstang(?:a|en)?"),
            item("dip-bars", "dips?-?stativ|dip ?bars?|dipsstenger|parallettes?"),
            item("kettlebell", "kettlebells?|kettlebjelle|kettlebjeller"),
            item("band", "strikk(?:er|ene)?|gummistrikk|resistance bands?|bands?"),
            item("bodyweight", "kroppsvekt|bodyweight|body weight"));

    private static final Pattern LIMIT = Pattern.compile("(?<!\\p{L})(?:bare|kun|eneste|only|just|nothing but)(?!\\p{L})");
    private static final Pattern NEGATION = Pattern.compile(
            "(?<!\\p{L})(?:ingen|uten|no|without|mangler|har ikke tilgang (?:på|til)|har ikke|ikke tilgang (?:på|til)|"
                    + "don'?t have|do not have|no access to|lacks?|lacking)(?!\\p{L})");
    private static final Pattern NO_EQUIPMENT = Pattern.compile(
            "(?<!\\p{L})(?:ingen utstyr|uten utstyr|no equipment|without equipment)(?!\\p{L})");
    private static final int LOOKAHEAD = 90;
    private static final int NEGATION_LOOKAHEAD = 40;

    private EquipmentNotes() {
    }

    /**
     * @param profileHave utstyret fra profilen, eller null når brukeren ikke er onboardet (ingen begrensning)
     * @return utstyret som skal gjelde; {@code fromText} er true når teksten endret det
     */
    static Result apply(String text, Set<String> profileHave) {
        if (text == null || text.isBlank()) {
            return new Result(profileHave, false);
        }
        String t = text.toLowerCase(Locale.ROOT);
        Set<String> have = profileHave;
        boolean changed = false;

        if (NO_EQUIPMENT.matcher(t).find()) {
            have = Set.of();
            changed = true;
        } else {
            Set<String> only = mentionedAfterLimit(t);
            if (only != null) {
                Set<String> tags = new HashSet<>(only);
                tags.retainAll(CATALOG_TAGS);
                if (only.contains("dumbbell")) {
                    tags.add("bench"); // «bare manualer» = manualer hjemme, som profilen: manualer og benk
                }
                if (only.contains("kettlebell")) {
                    tags.add("dumbbell"); // katalogen regner kettlebell-øvelser som manual-øvelser (kettlebell swing krever «dumbbell»)
                }
                have = tags;
                changed = true;
            }
        }

        Set<String> excluded = negated(t);
        if (!excluded.isEmpty()) {
            Set<String> base = new HashSet<>(have != null ? have : CATALOG_TAGS);
            if (base.removeAll(excluded)) {
                have = base;
                changed = true;
            } else if (have == null) {
                have = base; // ingen begrensning før, og det utelukkede utstyret er alt vi vet om
                changed = true;
            }
        }
        return new Result(have, changed);
    }

    /** Utstyr nevnt etter «bare/kun/only …» i samme setning, eller null når teksten ikke sier at det er alt hun har. */
    private static Set<String> mentionedAfterLimit(String t) {
        Set<String> found = null;
        Matcher m = LIMIT.matcher(t);
        while (m.find()) {
            Set<String> tags = itemsIn(window(t, m.end(), LOOKAHEAD));
            if (!tags.isEmpty()) {
                if (found == null) found = new HashSet<>();
                found.addAll(tags);
            }
        }
        if (found != null && found.equals(Set.of("bodyweight"))) {
            return new HashSet<>(); // «bare kroppsvekt»
        }
        return found;
    }

    /** Utstyr det står at brukeren mangler («ingen kabelmaskin», «uten stang», «no barbell»). */
    private static Set<String> negated(String t) {
        Set<String> out = new HashSet<>();
        Matcher m = NEGATION.matcher(t);
        while (m.find()) {
            out.addAll(itemsIn(window(t, m.end(), NEGATION_LOOKAHEAD)));
        }
        out.retainAll(CATALOG_TAGS);
        return out;
    }

    /** Teksten fra {@code from} til slutten av setningen (eller {@code max} tegn). */
    private static String window(String t, int from, int max) {
        int end = Math.min(t.length(), from + max);
        String w = t.substring(from, end);
        int stop = -1;
        for (char c : new char[]{'.', '!', '?', '\n', ';'}) {
            int i = w.indexOf(c);
            if (i >= 0 && (stop < 0 || i < stop)) stop = i;
        }
        return stop >= 0 ? w.substring(0, stop) : w;
    }

    private static Set<String> itemsIn(String s) {
        Set<String> found = new HashSet<>();
        String rest = s;
        for (Item it : ITEMS) {
            Matcher m = it.pattern().matcher(rest);
            if (m.find()) {
                found.add(it.tag());
                // Fjern det som er brukt, så «cable machine» ikke også teller som «machine» (kabel står før maskin i ITEMS).
                rest = m.replaceAll(" ");
            }
        }
        return found;
    }
}
