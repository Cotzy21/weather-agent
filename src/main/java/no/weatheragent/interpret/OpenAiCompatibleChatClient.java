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
 */
@Component
public class OpenAiCompatibleChatClient {

    private final RestClient http;
    private final String model;

    public OpenAiCompatibleChatClient(
            RestClient.Builder builder,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.model}") String model,
            @Value("${llm.api-key:}") String apiKey) {
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        // Sky-tjenester krever en API-nøkkel; lokal LM Studio/Ollama gjør ikke.
        // Tom nøkkel => ingen Authorization-header (så lokal kjøring er uendret).
        if (apiKey != null && !apiKey.isBlank()) {
            configured = configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.http = configured.build();
        this.model = model;
    }

    /** Send system- + bruker-melding, og returner modellens råtekst-svar (innholdet). */
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
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
