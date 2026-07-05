package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            List<Suggestion> workouts = new ArrayList<>();
            for (JsonNode w : json.path("workouts")) {
                if (workouts.size() >= MAX_PLAN_WORKOUTS) {
                    break;
                }
                String type = w.path("type").asText("STYRKE");
                workouts.add(new Suggestion(
                        w.path("title").asText("Økt"),
                        type.toUpperCase(Locale.ROOT),
                        w.has("content") ? w.get("content") : mapper.createObjectNode(),
                        w.path("rationale").asText("")));
            }
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
