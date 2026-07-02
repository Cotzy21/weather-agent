package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Tynn klient mot et OpenAI-kompatibelt chat-endepunkt. Samme RestClient-mønster
 * som MET/Open-Meteo. Fungerer mot lokal LM Studio (localhost:1234) eller Ollama
 * (localhost:11434) OG mot sky (Groq, OpenAI, OpenRouter ...) - kun ved å bytte
 * {@code llm.base-url}/{@code llm.model} og sette {@code llm.api-key} (HANDOFF §6).
 *
 * Modell-ruting ({@link LlmTier}): hvert kall oppgir om det trenger den billige
 * ({@code llm.model.fast}) eller den smarte ({@code llm.model.smart}) modellen.
 * Begge faller tilbake til {@code llm.model}, så ett-modells-oppsett er uendret.
 */
@Component
public class OpenAiCompatibleChatClient {

    private final RestClient http;
    private final String fastModel;
    private final String smartModel;

    public OpenAiCompatibleChatClient(
            RestClient.Builder builder,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.model.fast:${llm.model}}") String fastModel,
            @Value("${llm.model.smart:${llm.model}}") String smartModel,
            @Value("${llm.api-key:}") String apiKey) {
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        // Sky-tjenester krever en API-nøkkel; lokal LM Studio/Ollama gjør ikke.
        // Tom nøkkel => ingen Authorization-header (så lokal kjøring er uendret).
        if (apiKey != null && !apiKey.isBlank()) {
            configured = configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.http = configured.build();
        this.fastModel = fastModel;
        this.smartModel = smartModel;
    }

    /**
     * Om SMART faktisk er en annen modell enn FAST. Når begge peker på samme
     * modell (ett-modells-oppsett) er eskalering bare et bortkastet ekstra kall.
     */
    public boolean hasDedicatedSmartModel() {
        return !smartModel.equals(fastModel);
    }

    /** Send system- + bruker-melding til valgt modellnivå, og returner modellens råtekst-svar. */
    public String complete(LlmTier tier, String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", tier == LlmTier.SMART ? smartModel : fastModel,
                // 0 = mest mulig deterministisk; vi vil ha presis tolkning, ikke kreativitet.
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        JsonNode root = http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        return root.path("choices").path(0).path("message").path("content").asText("");
    }
}
