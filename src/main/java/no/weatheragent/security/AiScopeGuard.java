package no.weatheragent.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

/** A local, inexpensive gate for user text before any billable AI work. */
public final class AiScopeGuard {
    public enum Scope { WEATHER, TRAINING, ROUTE_STORY, EXERCISE_MATCH }

    private static final Logger log = LoggerFactory.getLogger(AiScopeGuard.class);
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\s]+");
    private static final Pattern OVERRIDE = Pattern.compile(
            "```|<\\s*/?\\s*(?:system|assistant|developer|instruction)s?\\s*>|"
            + "(?:\\[/?inst]|<\\|[^>]{1,35}\\|>)|(?:^|\\s)(?:system|developer|assistant)\\s*:|"
            + "(?:ignore|disregard|override|forget|bypass|ignorer|overstyr|glem|omgå).{0,48}"
            + "(?:instructions?|rules?|restrictions?|prompt|instruks|regl|begrensning|everything above|alt ovenfor|what you were told)|"
            + "(?:system|developer)\\s+(?:prompt|message|instructions?)|systemmelding|systemprompt|"
            + "(?:pretend|act as|roleplay|turn into|lat som).{0,35}(?:unrestricted|developer|programmer|coding assistant|uten regler)");
    private static final Pattern UNRELATED = Pattern.compile(
            "\\b(?:debug|debugge|feilsøk|refactor|refaktorer|leetcode|jailbreak|base64|dekod(?:e)?|decode|rot13)\\b|"
            + "\\b(?:write|generate|implement|solve|explain|return|create|make|run|execute|skriv|lag|løs|forklar|kjør)\\b.{0,60}"
            + "\\b(?:code|kode|function|funksjon|script|skript|algorithm|algoritme|programmeringsoppgave|"
            + "python|javascript|typescript|sql|html|css|regex|sorteringsalgoritme|essay|dikt|poem|joke|vits)\\b|"
            + "\\b(?:tell|fortell|translate|oversett|summarize|oppsummer)\\b.{0,60}"
            + "\\b(?:joke|vits|essay|article|artikkel|email|e-post|paragraph|avsnitt)\\b|"
            + "\\b(?:fix|fiks)\\b.{0,60}\\b(?:code|kode|script|skript|python|javascript|typescript|sql|html|css|regex)\\b|"
            + "\\b(?:python|javascript|typescript|sql|html|css)\\s+(?:code|kode|script|skript|function|funksjon)\\b|"
            + "(?:\\bdef\\s+\\w+\\s*\\(|\\bfunction\\s+\\w+\\s*\\(|\\bselect\\b.{0,45}\\bfrom\\b|"
            + "\\b(?:regn ut|calculate|solve)\\b.{0,18}\\d+\\s*[+*/=×÷−-]\\s*\\d+)|"
            + "\\b(?:api[ _-]?key|api[ _-]?nøkkel|secret token|access token|passord|password)\\b");
    private static final Pattern WEATHER = Pattern.compile(
            "(?<!\\p{L})(?:\\p{L}*vær\\p{L}*|varsel\\p{L}*|weather|forecast|rain|rainy|regn(?:et|er|vær|byger?)?|nedbør|"
            + "wind|vind\\p{L}*|temperatur\\p{L}*|temperature|snow|snø\\p{L}*|sunny|sol(?:a|en|skinn|fylt|oppgang|nedgang)?|"
            + "hike|hiking|fjelltur\\p{L}*|turrut\\p{L}*|tursti\\p{L}*|fjell|trail|trails|tur|turen|telte|camping|"
            + "i dag|i morgen|i overmorgen|helg\\p{L}*|neste uke|today|tomorrow|weekend|next week)(?!\\p{L})");
    // Short names are valid searches. Reject commands even if they fit the place-name shape.
    private static final Pattern PLACE = Pattern.compile("[\\p{L}][\\p{L}.'’\\-]*(?: [\\p{L}][\\p{L}.'’\\-]*){0,3}");
    private static final Pattern COMMAND = Pattern.compile(
            "^(?:write|generate|solve|explain|tell|give|make|create|translate|show|list|calculate|print|return|"
            + "skriv|lag|løs|forklar|fortell|gi|oversett|vis|regn|hva|hvordan|hvorfor|what|why|how)\\b");
    private static final Pattern TRAINING = Pattern.compile(
            "(?<!\\p{L})(?:\\p{L}*tren\\p{L}*|økt\\p{L}*|styrke\\p{L}*|workouts?|training|exercise\\p{L}*|"
            + "program|programmet|plans?|planen|sett|sets|reps|repetisjon\\p{L}*|muskl\\p{L}*|muscle\\p{L}*|"
            + "push|pull|legs?|bein|ben|squat\\p{L}*|press\\p{L}*|knebøy|markløft|deadlift|rows?|core|rygg|bryst|skuldre?|armer?|mage|biceps|triceps|back|chest|shoulders?|arms?|abs|"
            + "upper|lower|overkropp|underkropp|fullkropp|helkropp|full body|kroppsvekt|bodyweight|"
            + "manual\\p{L}*|dumbbells?|bench|benk|gym|utstyr|equipment|barbells?|kettlebells?|machines?|cables?|bands?|kabel\\p{L}*|maskin\\p{L}*|strikk|stang|"
            + "bjj|kampsport|cardio|kondisjon|løp\\p{L}*|run\\p{L}*|sykl\\p{L}*|cycl\\p{L}*|"
            + "swim\\p{L}*|svøm\\p{L}*|gåtur|hiking|mobilitet|mobility|stretching|yoga|minutt\\p{L}*|minutes?|nybegynner|beginner|hvile|rest|"
            + "mandag|tirsdag|onsdag|torsdag|fredag|lørdag|søndag|monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?!\\p{L})");
    private static final Pattern SHORT_REPLY = Pattern.compile(
            "(?:ja|nei|yes|no|ok|okay|gjerne|sure|kjør på|go ahead|hjemme|at home|ingen|none|no thanks|nei takk|same|samme|"
            + "(?:litt )?(?:lettere|tyngre|kortere|lengre)|(?:make it )?(?:easier|harder|shorter|longer)|"
            + "(?:[0-9]{1,3})(?:\\s*(?:kg|min|dager|days|ganger|times))?)[.!?]?");

    private AiScopeGuard() {}

    public static void weather(String text) {
        String value = checked(text, Scope.WEATHER, null);
        if (!WEATHER.matcher(value).find() && !(PLACE.matcher(value).matches() && !COMMAND.matcher(value).find())) {
            reject(Scope.WEATHER, "off_topic", null);
        }
    }

    public static void training(String text, String lang, boolean followUp) {
        String value = checked(text, Scope.TRAINING, lang);
        if (!TRAINING.matcher(value).find() && !(followUp && SHORT_REPLY.matcher(value).matches())) {
            reject(Scope.TRAINING, "off_topic", lang);
        }
    }

    /** Structured fields are data, not tasks; place and exercise names need not contain topic words. */
    public static void data(String text, Scope scope, String lang) {
        checked(text, scope, lang);
    }

    public static void planData(JsonNode plan, String lang) {
        if (plan == null || plan.isNull()) return;
        if (!plan.isObject() || plan.toString().length() > 32_000) reject(Scope.TRAINING, "invalid_plan", lang);
        ArrayDeque<JsonNode> nodes = new ArrayDeque<>();
        nodes.add(plan);
        while (!nodes.isEmpty()) {
            JsonNode node = nodes.removeFirst();
            if (node.isTextual()) data(node.textValue(), Scope.TRAINING, lang);
            else node.forEach(nodes::addLast);
        }
    }

    private static String checked(String text, Scope scope, String lang) {
        String value = text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        value = SPACES.matcher(INVISIBLE.matcher(value).replaceAll("")).replaceAll(" ").trim();
        if (OVERRIDE.matcher(value).find()) reject(scope, "instruction_override", lang);
        if (UNRELATED.matcher(value).find()) reject(scope, "unrelated_request", lang);
        return value;
    }

    private static void reject(Scope scope, String rule, String lang) {
        // Never log prompts, user data or secrets.
        log.info("AI input rejected: scope={} rule={}", scope, rule);
        boolean en = "en".equalsIgnoreCase(lang);
        throw new IllegalArgumentException(switch (scope) {
            case WEATHER -> "Værsøket svarer bare på spørsmål om vær og tur. Prøv for eksempel «Hvordan blir været i Ålesund i morgen?».";
            case TRAINING -> en ? "The training assistant can help with workouts and training plans. Ask about your training."
                    : "Treningsassistenten hjelper med økter og treningsplaner. Spør om treningen din.";
            case ROUTE_STORY -> en ? "Describe the route using its name and route facts."
                    : "Bruk bare rutenavn og rutefakta for å beskrive turen.";
            case EXERCISE_MATCH -> "Oppgi bare navn på treningsøvelser.";
        });
    }
}
