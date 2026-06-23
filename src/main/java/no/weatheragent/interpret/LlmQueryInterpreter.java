package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * QueryInterpreter som bruker en lokal LLM til selve språkforståelsen.
 *
 * Viktig (HANDOFF §6): faktaene - område og koordinater - hentes ALDRI fra
 * modellen, fordi små (og store) modeller hallusinerer norske stedsnavn og
 * koordinater. Modellen brukes KUN til å plukke ut fylke + datoer + intensjon.
 */
@Component
public class LlmQueryInterpreter implements QueryInterpreter {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final OpenAiCompatibleChatClient client;
    private final ObjectMapper mapper;

    public LlmQueryInterpreter(OpenAiCompatibleChatClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Interpretation interpret(String text) {
        LocalDate today = LocalDate.now(OSLO);
        String raw = client.complete(systemPrompt(today), text);

        try {
            JsonNode json = mapper.readTree(extractJsonObject(raw));
            return LlmInterpretationParser.parse(json, today);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Klarte ikke å tolke modellsvaret som JSON: " + raw, e);
        }
    }

    /** Instruksjonen til modellen. Dagens dato gis med, så relative datoer kan regnes ut. */
    private static String systemPrompt(LocalDate today) {
        return """
                Du tolker norske spørsmål om turvær og svarer KUN med ett JSON-objekt,
                uten forklaring og uten kodeblokk-tegn.

                Felter:
                  "region"   - norsk fylke spørsmålet gjelder (f.eks. "Møre og Romsdal").
                               Bruk null hvis det ikke nevnes.
                  "fromDate" - første aktuelle dag, ISO-format YYYY-MM-DD.
                  "toDate"   - siste aktuelle dag, ISO-format YYYY-MM-DD.
                  "tripType" - nøyaktig en av: FJELLTUR, LAVTUR, UANSETT.

                I dag er %s (tidssone Europe/Oslo). Regn ut relative datoer ut fra dette:
                "i helga" = nærmeste lørdag til søndag, "i morgen" = dagen etter i dag.
                Gjelder bare én dag, sett fromDate lik toDate.

                Eksempel på svar:
                {"region":"Møre og Romsdal","fromDate":"%s","toDate":"%s","tripType":"FJELLTUR"}
                """.formatted(today, today, today);
    }

    /**
     * Plukker ut det første {@code { ... }}-objektet i svaret. Små lokale modeller
     * pakker av og til JSON inn i prosa eller ```-kodeblokker.
     */
    private static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            return raw; // la JSON-parsing kaste en tydelig feil
        }
        return raw.substring(start, end + 1);
    }
}
