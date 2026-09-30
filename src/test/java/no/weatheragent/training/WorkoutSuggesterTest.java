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
    private final ReadinessService readiness = mock(ReadinessService.class);
    private final TrainingMemoryService memory = mock(TrainingMemoryService.class);
    private final TrainingProfileService profiles = mock(TrainingProfileService.class);
    private final WorkoutSuggester suggester = new WorkoutSuggester(repo, llm, mapper, readiness, memory, profiles);

    @org.junit.jupiter.api.BeforeEach
    void noSleepLoggedByDefault() {
        when(readiness.promptContext(any())).thenReturn("");
        when(memory.promptContext(any(), any())).thenReturn("");
        when(profiles.promptContext(any())).thenReturn("");
        when(profiles.tierFor(any())).thenReturn(LlmTier.SMART);
    }

    @Test
    void sleepContextIsSentToTheModel() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(readiness.promptContext(user)).thenReturn("\nSøvn siste netter: 2026-09-29 5.0 t\nDagsform i dag (fra søvn): LAV");
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn(
                "{\"title\":\"Rolig\",\"type\":\"STYRKE\",\"content\":{\"blocks\":[]},\"rationale\":\"\"}");

        suggester.suggest(user, "styrke", "STYRKE", null);

        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llm).complete(eq(LlmTier.SMART), any(), prompt.capture());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.getValue().contains("Dagsform i dag (fra søvn): LAV"));
    }
    private final UUID user = UUID.randomUUID();

    @Test
    void trainingMemoryIsSentToTheModel() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(memory.promptContext(eq(user), any())).thenReturn("\nBrukerminne:\n- Liker IKKE (ikke foreslå): Burpees");
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn(
                "{\"title\":\"Økt\",\"type\":\"STYRKE\",\"content\":{\"blocks\":[]},\"rationale\":\"\"}");

        suggester.suggest(user, "styrke", "STYRKE", null);

        org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llm).complete(eq(LlmTier.SMART), system.capture(), prompt.capture());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.getValue().contains("Liker IKKE (ikke foreslå): Burpees"));
        org.junit.jupiter.api.Assertions.assertTrue(system.getValue().contains("ALDRI øvelser under «Liker IKKE»"));
    }

    @Test
    void parsesModelJsonIntoSuggestion() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                Her er forslaget:
                {"title":"Beinøkt","type":"styrke",
                 "content":{"blocks":[{"kind":"exercise","name":"Knebøy","sets":[{"reps":5,"weightKg":100}]}]},
                 "rationale":"Fokus på bein."}
                """);

        Suggestion s = suggester.suggest(user, "større bein", null, null);

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

        assertThrows(AiSuggestionException.class, () -> suggester.suggest(user, "fokus", null, null));
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

        PlanSuggestion plan = suggester.suggestPlan(user, "lag en push pull legs split", null);

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

        PlanSuggestion plan = suggester.suggestPlan(user, "gi meg 12 økter", null);

        assertTrue(plan.workouts().size() <= 7);
    }

    @Test
    void planThrowsWhenNoWorkouts() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any()))
                .thenReturn("{\"title\":\"Tom\",\"summary\":\"\",\"workouts\":[]}");

        assertThrows(AiSuggestionException.class, () -> suggester.suggestPlan(user, "noe", null));
    }

    @Test
    void chatReturnsFollowUpQuestionsWhenModelAsks() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                {"reply":"Så klart! Et par spørsmål først.",
                 "questions":["Hvor mange dager i uka?","Har du tilgang til vektstang?"],
                 "plan":null}
                """);

        AssistantReply r = suggester.chat(user, List.of(new ChatTurn("user", "lag et program")), null, null);

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
                new ChatTurn("user", "to")), null, null);

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
                () -> suggester.chat(user, List.of(new ChatTurn("user", "hei")), null, null));
    }

    @Test
    void singleSetExercisesAreExpandedToThreeSets() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                {"title":"Pull","type":"STYRKE","rationale":"",
                 "content":{"blocks":[
                   {"kind":"exercise","name":"Rows","sets":[{"reps":8,"weightKg":40}]},
                   {"kind":"exercise","name":"Curls","sets":[{"reps":10,"weightKg":12},{"reps":10,"weightKg":12},{"reps":10,"weightKg":12},{"reps":8,"weightKg":12}]},
                   {"kind":"superset","rounds":3,"exercises":[{"name":"A","sets":[{"reps":10,"weightKg":10}]}]}
                 ]}}
                """);

        Suggestion s = suggester.suggest(user, "pull", "STYRKE", null);

        var blocks = s.content().path("blocks");
        assertEquals(3, blocks.get(0).path("sets").size());
        assertEquals(40, blocks.get(0).path("sets").get(2).path("weightKg").asInt());
        assertEquals(4, blocks.get(1).path("sets").size());
        assertEquals(1, blocks.get(2).path("exercises").get(0).path("sets").size());
    }

    @Test
    void planKeepsTimeOfDaySoTwoSessionsCanShareADay() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("""
                {"reply":"Ok.","questions":[],
                 "plan":{"title":"Upper/Lower + BJJ","summary":"","workouts":[
                   {"title":"Upper","day":"tuesday","time":"morning","type":"STYRKE","content":{"blocks":[]},"rationale":""},
                   {"title":"BJJ","day":"tuesday","time":"evening","type":"KAMPSPORT","content":{"durationMin":90},"rationale":""}
                 ]}}
                """);

        AssistantReply r = suggester.chat(user, List.of(new ChatTurn("user", "upper/lower + BJJ tuesday evening")), null, "en");

        var bjj = r.plan().workouts().get(1);
        assertEquals("KAMPSPORT", bjj.type());
        assertEquals("evening", bjj.content().path("time").asText());
        assertEquals("tuesday", bjj.content().path("day").asText());
    }

    @Test
    void currentPlanAndLanguageAreSentToTheModel() throws Exception {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(llm.complete(eq(LlmTier.SMART), any(), any())).thenReturn("{\"reply\":\"Ok.\",\"questions\":[\"?\"]}");
        var current = mapper.readTree("{\"title\":\"PPL\",\"workouts\":[{\"title\":\"Push\",\"day\":\"monday\"}]}");

        suggester.chat(user, List.of(new ChatTurn("user", "move push to tuesday")), current, "en");

        org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llm).complete(eq(LlmTier.SMART), system.capture(), prompt.capture());
        assertTrue(prompt.getValue().contains("Nåværende plan (JSON)"));
        assertTrue(prompt.getValue().contains("\"title\":\"Push\""));
        assertTrue(prompt.getValue().contains("på ENGELSK"));
        assertTrue(system.getValue().contains("endre BARE"));
        assertTrue(system.getValue().contains("KAMPSPORT"));
    }

    @Test
    void profileIsSentToTheModelAndPicksTheTier() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        when(profiles.promptContext(user)).thenReturn("\nTreningsprofil (fra onboarding):\n- Erfaringsnivå: nybegynner");
        when(profiles.tierFor(user)).thenReturn(LlmTier.PRO);
        when(llm.complete(eq(LlmTier.PRO), any(), any())).thenReturn(
                "{\"title\":\"Økt\",\"type\":\"STYRKE\",\"content\":{\"blocks\":[]},\"rationale\":\"\"}");

        suggester.suggest(user, "styrke", "STYRKE", null);

        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llm).complete(eq(LlmTier.PRO), any(), prompt.capture());
        assertTrue(prompt.getValue().contains("Erfaringsnivå: nybegynner"));
    }
}
