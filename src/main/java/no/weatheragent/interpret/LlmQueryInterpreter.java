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
                  "region"   - norsk fylke, kommune ELLER nasjonalpark spørsmålet gjelder,
                               med offisielt navn (f.eks. "Møre og Romsdal", "Stranda",
                               "Jotunheimen nasjonalpark"). Ta med "nasjonalpark" i navnet
                               når det er en park. Bruk null hvis det ikke nevnes.
                  "when"     - tidsuttrykket, NØYAKTIG én av disse kodene:
                               I_DAG, I_MORGEN, I_OVERMORGEN, HELGA, NESTE_HELG,
                               DENNE_UKA, NESTE_UKE, KONKRET, UKJENT.
                               IKKE regn ut datoer selv - velg bare riktig kode.
                               (HELGA = denne helga, NESTE_HELG = helga etter.)
                  "fromDate"/"toDate" - KUN når brukeren nevner en konkret dato
                               (when=KONKRET), ISO YYYY-MM-DD. Ellers null.
                  "tripType" - nøyaktig én av: FJELLTUR, LAVTUR, UANSETT.

                I dag er %s (tidssone Europe/Oslo) - bruk det bare til å fylle inn
                årstall ved konkrete datoer.

                Eksempler:
                  "hvor blir det best vær i Rogaland neste uke"
                    -> {"region":"Rogaland","when":"NESTE_UKE","fromDate":null,"toDate":null,"tripType":"UANSETT"}
                  "fint fjellvær i Møre og Romsdal i helga"
                    -> {"region":"Møre og Romsdal","when":"HELGA","fromDate":null,"toDate":null,"tripType":"FJELLTUR"}
                  "været på Sunnmøre 3. juli"
                    -> {"region":"Sunnmøre","when":"KONKRET","fromDate":"%s-07-03","toDate":"%s-07-03","tripType":"UANSETT"}
                """.formatted(today, today.getYear(), today.getYear());
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
