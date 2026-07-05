package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.weatheragent.interpret.LlmTier;
import no.weatheragent.interpret.OpenAiCompatibleChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lager et AI-treningsforslag ut fra brukerens fokus og nylige økter. Gjenbruker
 * LLM-laget (samme klient som vær-tolkningen). Historikken gir modellen kontekst
 * for progressiv overload (litt mer enn forrige gang).
 */
@Service
public class WorkoutSuggester {

    private static final int HISTORY_LIMIT = 8;
    /** Tak på antall økter i en generert plan (mot både uhell og misbruk av tokens). */
    private static final int MAX_PLAN_WORKOUTS = 7;

    private final WorkoutRepository repository;
    private final OpenAiCompatibleChatClient llm;
    private final ObjectMapper mapper;

    public WorkoutSuggester(WorkoutRepository repository, OpenAiCompatibleChatClient llm, ObjectMapper mapper) {
        this.repository = repository;
        this.llm = llm;
        this.mapper = mapper;
    }

    public Suggestion suggest(UUID userId, String focus, String type) {
        String history = recentHistory(userId);
        // Forslag krever resonnering over historikken (progressiv overload) -> smart modell.
        String raw = llm.complete(LlmTier.SMART, systemPrompt(), userPrompt(focus, type, history));
        try {
            JsonNode json = mapper.readTree(extractJson(raw));
            String resolvedType = json.path("type").asText("");
            if (resolvedType.isBlank()) {
                resolvedType = (type == null || type.isBlank()) ? "STYRKE" : type;
            }
            return new Suggestion(
                    json.path("title").asText("Forslag"),
                    resolvedType.toUpperCase(Locale.ROOT),
                    json.has("content") ? json.get("content") : mapper.createObjectNode(),
                    json.path("rationale").asText(""));
        } catch (Exception e) {
            throw new AiSuggestionException("Klarte ikke å tolke AI-forslaget. Prøv igjen.");
        }
    }

    /**
     * Lag en KOMPLETT plan med én eller flere økter ut fra en fritekst-forespørsel
     * («lag en push pull legs split», «upper/lower, mer cardio»). Bruker historikken
     * for progressiv overload. Titler/begrunnelser kommer på samme språk som
     * forespørselen (modellen speiler språket - backend-i18n kommer senere).
     */
    public PlanSuggestion suggestPlan(UUID userId, String request) {
        String history = recentHistory(userId);
        String raw = llm.complete(LlmTier.SMART, planSystemPrompt(), planUserPrompt(request, history));
        try {
            JsonNode json = mapper.readTree(extractJson(raw));
            List<Suggestion> workouts = parseWorkouts(json);
            if (workouts.isEmpty()) {
                throw new AiSuggestionException("Klarte ikke å lage en plan. Beskriv ønsket tydeligere.");
            }
            return new PlanSuggestion(
                    json.path("title").asText("Treningsplan"),
                    json.path("summary").asText(""),
                    workouts);
        } catch (AiSuggestionException e) {
            throw e;
        } catch (Exception e) {
            throw new AiSuggestionException("Klarte ikke å tolke planen. Prøv igjen.");
        }
    }

    /**
     * Samtale-basert assistent: gitt samtalen så langt + brukerens historikk,
     * svarer modellen med ENTEN oppfølgingsspørsmål ELLER en komplett plan.
     * Progressiv overload fra historikken, og hver plan-økt får en foreslått ukedag.
     */
    public AssistantReply chat(UUID userId, List<ChatTurn> messages) {
        String history = recentHistory(userId);
        String raw = llm.complete(LlmTier.SMART, assistantSystemPrompt(), assistantUserPrompt(history, messages));
        try {
            JsonNode json = mapper.readTree(extractJson(raw));

            List<String> questions = new ArrayList<>();
            for (JsonNode q : json.path("questions")) {
                String s = q.asText("").trim();
                if (!s.isBlank() && questions.size() < 5) {
                    questions.add(s);
                }
            }

            PlanSuggestion plan = null;
            JsonNode planNode = json.path("plan");
            if (planNode.path("workouts").isArray()) {
                List<Suggestion> workouts = parseWorkouts(planNode);
                if (!workouts.isEmpty()) {
                    plan = new PlanSuggestion(
                            planNode.path("title").asText("Treningsplan"),
                            planNode.path("summary").asText(""),
                            workouts);
                }
            }

            String reply = json.path("reply").asText("");
            if (reply.isBlank() && plan == null && questions.isEmpty()) {
                throw new AiSuggestionException("Assistenten svarte uforståelig. Prøv igjen.");
            }
            return new AssistantReply(reply, questions, plan);
        } catch (AiSuggestionException e) {
            throw e;
        } catch (Exception e) {
            throw new AiSuggestionException("Klarte ikke å tolke assistentens svar. Prøv igjen.");
        }
    }

    /** Bygg øktene fra en node med et {@code workouts}-array. Foreslått ukedag ("day")
     *  legges i innholdet så frontenden kan planlegge den (byggeren ignorerer den). */
    private List<Suggestion> parseWorkouts(JsonNode node) {
        List<Suggestion> workouts = new ArrayList<>();
        for (JsonNode w : node.path("workouts")) {
            if (workouts.size() >= MAX_PLAN_WORKOUTS) {
                break;
            }
            JsonNode content = w.path("content").isObject() ? w.get("content") : mapper.createObjectNode();
            String day = w.path("day").asText("").trim();
            if (!day.isBlank() && content instanceof ObjectNode obj) {
                obj.put("day", day);
            }
            workouts.add(new Suggestion(
                    w.path("title").asText("Økt"),
                    w.path("type").asText("STYRKE").toUpperCase(Locale.ROOT),
                    content,
                    w.path("rationale").asText("")));
        }
        return workouts;
    }

    private String recentHistory(UUID userId) {
        List<Workout> recent = repository.findByUserIdOrderByDateDescCreatedAtDesc(userId);
        if (recent.isEmpty()) {
            return "Ingen tidligere økter logget.";
        }
        StringBuilder sb = new StringBuilder();
        recent.stream().limit(HISTORY_LIMIT).forEach(w -> sb
                .append("- ").append(w.getDate()).append(' ').append(w.getType())
                .append(" \"").append(w.getTitle()).append("\": ").append(summarize(w)).append('\n'));
        return sb.toString();
    }

    private static String summarize(Workout w) {
        JsonNode c = w.getContent();
        if ("STYRKE".equalsIgnoreCase(w.getType())) {
            List<String> parts = new ArrayList<>();
            for (JsonNode b : c.path("blocks")) {
                if ("superset".equals(b.path("kind").asText(""))) {
                    for (JsonNode ex : b.path("exercises")) {
                        parts.add(ex.path("name").asText() + " " + topWeight(ex.path("sets")));
                    }
                } else {
                    JsonNode sets = b.path("sets").isMissingNode() ? b.path("drops") : b.path("sets");
                    parts.add(b.path("name").asText() + " " + topWeight(sets));
                }
            }
            return String.join(", ", parts);
        }
        StringBuilder s = new StringBuilder();
        if (c.has("distanceKm")) s.append(c.get("distanceKm").asText()).append(" km ");
        if (c.has("durationMin")) s.append(c.get("durationMin").asText()).append(" min");
        return s.toString().trim();
    }

    private static String topWeight(JsonNode sets) {
        double max = 0;
        for (JsonNode s : sets) {
            max = Math.max(max, s.path("weightKg").asDouble(0));
        }
        return max > 0 ? "(maks " + max + " kg)" : "";
    }

    private static String systemPrompt() {
        return """
                Du er en erfaren treningsassistent. Lag ETT konkret økt-forslag ut fra
                brukerens fokus og nylige økter. Bruk progressiv overload: foreslå litt mer
                (vekt eller reps) enn forrige gang for øvelser brukeren allerede gjør.

                Svar KUN med ett JSON-objekt, ingen tekst utenfor:
                {
                  "title": "...",
                  "type": "STYRKE | LØPING | SVØMMING | SYKKEL | BULDRING | HIKING | FRISTIL",
                  "content": { ... },
                  "rationale": "kort norsk begrunnelse"
                }
                For STYRKE skal content være {"blocks":[ ... ]} der hver blokk er én av:
                  {"kind":"exercise","name":"...","sets":[{"reps":5,"weightKg":80}]}
                  {"kind":"dropset","name":"...","drops":[{"reps":10,"weightKg":15}]}
                  {"kind":"superset","exercises":[{"name":"...","sets":[{"reps":8,"weightKg":20}]}]}
                For kondisjon: {"distanceKm":5,"durationMin":30} (HIKING kan ha "ascentM").
                """;
    }

    private static String planSystemPrompt() {
        return """
                Du er en erfaren personlig trener. Brukeren beskriver hva de vil, og du
                lager en KOMPLETT treningsplan med ÉN ELLER FLERE økter. Antall økter skal
                passe forespørselen:
                  - "push pull legs" / "ppl"     -> 3 økter (push, pull, bein)
                  - "upper/lower" / "overkropp/underkropp" -> 2 økter
                  - "helkropp 3x"                -> 3 balanserte helkroppsøkter
                  - en enkelt økt                -> 1 økt
                Legg til kondisjonsøkt(er) hvis brukeren nevner mer cardio/utholdenhet.

                Regler:
                  - Bruk brukerens nylige økter til progressiv overload (litt mer enn sist
                    på øvelser de allerede gjør). IKKE default til bryst/push om de ikke ba om det.
                  - Balanser muskelgrupper fornuftig innen og på tvers av øktene.
                  - Skriv "title", "summary" og alle "rationale" på SAMME SPRÅK som brukerens
                    forespørsel (engelsk forespørsel -> engelsk svar).

                Svar KUN med ett JSON-objekt, ingen tekst utenfor:
                {
                  "title": "kort navn på planen",
                  "summary": "1-2 setninger om planen",
                  "workouts": [
                    {
                      "title": "...",
                      "type": "STYRKE | LØPING | SVØMMING | SYKKEL | BULDRING | HIKING | FRISTIL",
                      "content": { ... },
                      "rationale": "kort begrunnelse"
                    }
                  ]
                }
                For STYRKE skal content være {"blocks":[ ... ]} der hver blokk er én av:
                  {"kind":"exercise","name":"...","sets":[{"reps":8,"weightKg":60}]}
                  {"kind":"dropset","name":"...","drops":[{"reps":10,"weightKg":20}]}
                  {"kind":"superset","rounds":3,"exercises":[{"name":"...","sets":[{"reps":10,"weightKg":15}]}]}
                For kondisjon: {"distanceKm":5,"durationMin":30} (HIKING kan ha "ascentM").
                """;
    }

    private static String planUserPrompt(String request, String history) {
        return "Forespørsel: " + (request == null ? "" : request)
                + "\n\nNylige økter:\n" + history;
    }

    private static String assistantSystemPrompt() {
        return """
                Du er en erfaren personlig trener som har en SAMTALE med brukeren for å
                lage et treningsopplegg. Du får brukerens nylige økter som kontekst.

                Hver tur velger du ÉN av to ting:
                  A) Mangler du viktig info for et godt opplegg (mål, antall dager per uke,
                     utstyr, skader/begrensninger, erfaringsnivå)? STILL 1-3 korte
                     oppfølgingsspørsmål i stedet for å gjette. Ikke still mer enn nødvendig.
                  B) Har du nok? LAG en komplett plan med én eller flere økter.
                Er forespørselen allerede tydelig (f.eks. "lag en push pull legs split"),
                lag planen med en gang uten å spørre.

                Regler for planen:
                  - PROGRESSIV OVERLOAD: for øvelser brukeren allerede gjør, foreslå litt mer
                    (vekt eller reps) enn forrige gang, og nevn det i begrunnelsen.
                  - Håndter endringer: "bygg om PPL til upper/lower", "legg til mer cardio",
                    "bytt ut knebøy" - bruk øvelsene og nivået fra historikken.
                  - Antall økter passer forespørselen (ppl=3, upper/lower=2, helkropp osv.).
                  - Gi hver økt en foreslått ukedag ("day") og fordel dem fornuftig utover uka
                    (hvile mellom like muskelgrupper) når planen har flere økter.
                  - Skriv ALT (reply, spørsmål, titler, begrunnelser) på SAMME SPRÅK som brukeren.

                Svar KUN med ett JSON-objekt, ingen tekst utenfor:
                {
                  "reply": "kort svar til brukeren (1-3 setninger)",
                  "questions": ["...", "..."],
                  "plan": {
                    "title": "...",
                    "summary": "...",
                    "workouts": [
                      { "title": "...", "day": "mandag",
                        "type": "STYRKE | LØPING | SVØMMING | SYKKEL | BULDRING | HIKING | FRISTIL",
                        "content": { ... }, "rationale": "..." }
                    ]
                  }
                }
                Bruk "questions" (og utelat/null "plan") når du spør. Bruk "plan" (og tomt
                "questions") når du lager opplegget.
                For STYRKE skal content være {"blocks":[ ... ]} der hver blokk er én av:
                  {"kind":"exercise","name":"...","sets":[{"reps":8,"weightKg":60}]}
                  {"kind":"dropset","name":"...","drops":[{"reps":10,"weightKg":20}]}
                  {"kind":"superset","rounds":3,"exercises":[{"name":"...","sets":[{"reps":10,"weightKg":15}]}]}
                For kondisjon: {"distanceKm":5,"durationMin":30} (HIKING kan ha "ascentM").
                """;
    }

    /** Flater samtalen til ett bruker-prompt (klienten er enkel/enkelt-tur). */
    private static String assistantUserPrompt(String history, List<ChatTurn> messages) {
        StringBuilder sb = new StringBuilder("Nylige økter:\n").append(history).append("\n\nSamtale så langt:\n");
        int start = Math.max(0, messages.size() - 20); // tak: siste 20 meldinger
        for (ChatTurn m : messages.subList(start, messages.size())) {
            String who = "assistant".equalsIgnoreCase(m.role()) ? "Assistent" : "Bruker";
            String content = m.content() == null ? "" : m.content();
            if (content.length() > 2000) {
                content = content.substring(0, 2000);
            }
            sb.append(who).append(": ").append(content).append('\n');
        }
        return sb.toString();
    }

    private static String userPrompt(String focus, String type, String history) {
        return "Fokus: " + (focus == null ? "" : focus)
                + (type == null || type.isBlank() ? "" : "\nØnsket type: " + type)
                + "\n\nNylige økter:\n" + history;
    }

    private static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : raw;
    }
}
