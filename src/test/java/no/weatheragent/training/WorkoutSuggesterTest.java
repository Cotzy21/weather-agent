package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.interpret.LlmTier;
import no.weatheragent.interpret.OpenAiCompatibleChatClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
