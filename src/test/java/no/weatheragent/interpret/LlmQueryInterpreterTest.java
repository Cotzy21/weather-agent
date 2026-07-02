package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modell-ruting i tolkningen: FAST brukes først, og SMART prøves ÉN gang hvis
 * fast-modellens svar ikke lar seg parse - men bare når SMART faktisk er en
 * annen modell. Selve JSON->Interpretation-logikken testes i
 * {@link LlmInterpretationParserTest}.
 */
class LlmQueryInterpreterTest {

    private static final String VALID_JSON = """
            {"region":"Rogaland","country":"NO","when":"I_MORGEN","target":"STED",
             "fromDate":null,"toDate":null,"tripType":"UANSETT"}
            """;

    private final OpenAiCompatibleChatClient client = mock(OpenAiCompatibleChatClient.class);
    private final LlmQueryInterpreter interpreter = new LlmQueryInterpreter(client, new ObjectMapper());

    @Test
    void usesFastModelWhenAnswerParses() {
        when(client.complete(eq(LlmTier.FAST), any(), any())).thenReturn(VALID_JSON);

        Interpretation i = interpreter.interpret("vær i Rogaland i morgen");

        assertEquals("Rogaland", i.region());
        assertEquals("NO", i.country());
        assertEquals(TimeExpression.I_MORGEN, i.when());
        verify(client, never()).complete(eq(LlmTier.SMART), any(), any());
    }

    @Test
    void escalatesToSmartWhenFastAnswerIsUnparseable() {
        when(client.hasDedicatedSmartModel()).thenReturn(true);
        when(client.complete(eq(LlmTier.FAST), any(), any())).thenReturn("beklager, her kommer ingen JSON");
        when(client.complete(eq(LlmTier.SMART), any(), any())).thenReturn(VALID_JSON);

        Interpretation i = interpreter.interpret("vær i Rogaland i morgen");

        assertEquals("Rogaland", i.region());
    }

    @Test
    void failsWithoutRetryWhenSmartIsSameModel() {
        when(client.hasDedicatedSmartModel()).thenReturn(false);
        when(client.complete(eq(LlmTier.FAST), any(), any())).thenReturn("ikke JSON");

        assertThrows(IllegalStateException.class, () -> interpreter.interpret("vær i Rogaland"));
        verify(client, never()).complete(eq(LlmTier.SMART), any(), any());
    }

    @Test
    void failsWhenSmartAnswerIsAlsoUnparseable() {
        when(client.hasDedicatedSmartModel()).thenReturn(true);
        when(client.complete(any(), any(), any())).thenReturn("fortsatt ikke JSON");

        assertThrows(IllegalStateException.class, () -> interpreter.interpret("vær i Rogaland"));
    }
}
