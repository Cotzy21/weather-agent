package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.interpret.LlmTier;
import no.weatheragent.interpret.OpenAiCompatibleChatClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkoutSuggesterTest {

    private final WorkoutRepository repo = mock(WorkoutRepository.class);
    private final OpenAiCompatibleChatClient llm = mock(OpenAiCompatibleChatClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkoutSuggester suggester = new WorkoutSuggester(repo, llm, mapper);
    private final UUID user = UUID.randomUUID();

    @Test
    void parsesModelJsonIntoSuggestion() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                Her er forslaget:
                {"title":"Beinøkt","type":"styrke",
                 "content":{"blocks":[{"kind":"exercise","name":"Knebøy","sets":[{"reps":5,"weightKg":100}]}]},
                 "rationale":"Fokus på bein."}
                """);

        Suggestion s = suggester.suggest(user, "større bein", null);

        assertEquals("Beinøkt", s.title());
        assertEquals("STYRKE", s.type()); // normalisert til store bokstaver
        assertEquals("Fokus på bein.", s.rationale());
        assertTrue(s.content().path("blocks").isArray());
        assertEquals("Knebøy", s.content().path("blocks").get(0).path("name").asText());
    }

    @Test
    void throwsWhenModelReturnsNonJson() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("beklager, jeg klarte ikke");

        assertThrows(AiSuggestionException.class, () -> suggester.suggest(user, "fokus", null));
    }

    @Test
    void planParsesMultipleWorkouts() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                {"title":"Push/Pull/Legs","summary":"3-dagers split.",
                 "workouts":[
                   {"title":"Push","type":"styrke","content":{"blocks":[{"kind":"exercise","name":"Benkpress","sets":[{"reps":8,"weightKg":70}]}]},"rationale":"Bryst/skulder/triceps."},
                   {"title":"Pull","type":"STYRKE","content":{"blocks":[]},"rationale":"Rygg/biceps."},
                   {"title":"Legs","type":"STYRKE","content":{"blocks":[]},"rationale":"Bein."}
                 ]}
                """);

        PlanSuggestion plan = suggester.suggestPlan(user, "lag en push pull legs split");

        assertEquals("Push/Pull/Legs", plan.title());
        assertEquals(3, plan.workouts().size());
        assertEquals("Push", plan.workouts().getFirst().title());
        assertEquals("STYRKE", plan.workouts().getFirst().type()); // normalisert
        assertEquals("Benkpress", plan.workouts().getFirst().content()
                .path("blocks").get(0).path("name").asText());
    }

    @Test
    void planCapsNumberOfWorkouts() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        StringBuilder ws = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            ws.append(i > 0 ? "," : "").append("{\"title\":\"Økt ").append(i).append("\",\"type\":\"STYRKE\",\"content\":{}}");
        }
        when(llm.complete(eq(LlmTier.SMART), any(), any()))
                .thenReturn("{\"title\":\"Stor plan\",\"summary\":\"\",\"workouts\":[" + ws + "]}");

        PlanSuggestion plan = suggester.suggestPlan(user, "gi meg 12 økter");

        assertTrue(plan.workouts().size() <= 7);
    }

    @Test
    void planThrowsWhenNoWorkouts() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any()))
                .thenReturn("{\"title\":\"Tom\",\"summary\":\"\",\"workouts\":[]}");

        assertThrows(AiSuggestionException.class, () -> suggester.suggestPlan(user, "noe"));
    }

    @Test
    void chatReturnsFollowUpQuestionsWhenModelAsks() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                {"reply":"Så klart! Et par spørsmål først.",
                 "questions":["Hvor mange dager i uka?","Har du tilgang til vektstang?"],
                 "plan":null}
                """);

        AssistantReply r = suggester.chat(user, List.of(new ChatTurn("user", "lag et program")));

        assertEquals(2, r.questions().size());
        assertNull(r.plan());
        assertTrue(r.reply().startsWith("Så klart"));
    }

    @Test
    void chatReturnsPlanWithSuggestedDay() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                {"reply":"Her er et opplegg.","questions":[],
                 "plan":{"title":"Upper/Lower","summary":"2-dagers.","workouts":[
                   {"title":"Upper","day":"mandag","type":"STYRKE","content":{"blocks":[]},"rationale":"Overkropp."},
                   {"title":"Lower","day":"torsdag","type":"STYRKE","content":{"blocks":[]},"rationale":"Underkropp."}
                 ]}}
                """);

        AssistantReply r = suggester.chat(user, List.of(
                new ChatTurn("user", "lag en upper lower split"),
                new ChatTurn("assistant", "Hvor mange dager?"),
                new ChatTurn("user", "to")));

        assertTrue(r.questions().isEmpty());
        assertEquals("Upper/Lower", r.plan().title());
        assertEquals(2, r.plan().workouts().size());
        assertEquals("mandag", r.plan().workouts().getFirst().content().path("day").asText());
    }

    @Test
    void chatThrowsWhenModelReturnsNonJson() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("???");

        assertThrows(AiSuggestionException.class,
                () -> suggester.chat(user, List.of(new ChatTurn("user", "hei"))));
    }
}
