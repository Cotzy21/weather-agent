package no.weatheragent.interpret;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifiserer modell-rutingen: at riktig modellnavn sendes i request-body per
 * {@link LlmTier}, uten nettverk (MockRestServiceServer, som resten av klientene).
 */
class OpenAiCompatibleChatClientTest {

    private static final String OK_RESPONSE = """
            {"choices":[{"message":{"content":"ok"}}]}
            """;

    @Test
    void fastTierSendsFastModelName() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                builder, "http://llm.test/v1", "mini-modell", "stor-modell", "");

        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("mini-modell"))
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        assertEquals("ok", client.complete(LlmTier.FAST, "system", "bruker"));
        server.verify();
    }

    @Test
    void smartTierSendsSmartModelName() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                builder, "http://llm.test/v1", "mini-modell", "stor-modell", "");

        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("stor-modell"))
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        assertEquals("ok", client.complete(LlmTier.SMART, "system", "bruker"));
        server.verify();
    }

    @Test
    void smartTierIsDedicatedOnlyWhenModelsDiffer() {
        OpenAiCompatibleChatClient same = new OpenAiCompatibleChatClient(
                RestClient.builder(), "http://llm.test/v1", "en-modell", "en-modell", "");
        OpenAiCompatibleChatClient different = new OpenAiCompatibleChatClient(
                RestClient.builder(), "http://llm.test/v1", "mini-modell", "stor-modell", "");

        assertFalse(same.hasDedicatedSmartModel());
        assertTrue(different.hasDedicatedSmartModel());
    }
}
