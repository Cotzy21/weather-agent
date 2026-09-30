package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
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
 * PRO ({@code llm.model.pro}) er den sterkeste, for brukere som trenger mest veiledning, og faller
 * tilbake til smart. Alt faller tilbake til {@code llm.model}, så ett-modells-oppsett er uendret.
 */
@Component
public class OpenAiCompatibleChatClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiCompatibleChatClient.class);

    static final int MAX_OUTPUT_TOKENS = 6000;

    private final RestClient http;
    private final String fastModel;
    private final String smartModel;
    private final String proModel;

    public OpenAiCompatibleChatClient(
            RestClient.Builder builder,
            String baseUrl,
            String fastModel,
            String smartModel,
            String apiKey) {
        this(builder, baseUrl, fastModel, smartModel, smartModel, apiKey);
    }

    @Autowired
    public OpenAiCompatibleChatClient(
            RestClient.Builder builder,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.model.fast:${llm.model}}") String fastModel,
            @Value("${llm.model.smart:${llm.model}}") String smartModel,
            @Value("${llm.model.pro:${llm.model.smart:${llm.model}}}") String proModel,
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
        this.proModel = proModel;
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
                "model", switch (tier) {
                    case FAST -> fastModel;
                    case SMART -> smartModel;
                    case PRO -> proModel;
                },
                // 0 = mest mulig deterministisk; vi vil ha presis tolkning, ikke kreativitet.
                "temperature", 0,
                // Tak paa svaret: en manipulert prompt (eller en modell som spinner) kan ellers gi
                // uendelig lange svar og ubegrenset kostnad. En full plan er ca. 3-4000 tokens.
                "max_tokens", MAX_OUTPUT_TOKENS,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        long started = System.nanoTime();
        JsonNode root = http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        log.info("LLM {} svarte på {} ms", tier, (System.nanoTime() - started) / 1_000_000);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }
}
