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
 * modellen, fordi små (og store) modeller hallusinerer stedsnavn og
 * koordinater. Modellen brukes KUN til å plukke ut regionnavn + land + datoer
 * + intensjon; selve oppslaget skjer i OSM.
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
        String prompt = systemPrompt(today);

        // Tolkning er en stram ekstraksjonsoppgave -> billig modell holder som regel.
        String raw = client.complete(LlmTier.FAST, prompt, text);
        try {
            return parse(raw, today);
        } catch (Exception fastFailed) {
            if (!client.hasDedicatedSmartModel()) {
                throw unparseable(raw, fastFailed);
            }
            // Eskalering: den billige modellen svarte uparserbart -> ETT forsøk til
            // med den smarte, før vi gir opp.
            String retried = client.complete(LlmTier.SMART, prompt, text);
            try {
                return parse(retried, today);
            } catch (Exception smartFailed) {
                throw unparseable(retried, smartFailed);
            }
        }
    }

    private Interpretation parse(String raw, LocalDate today) throws Exception {
        JsonNode json = mapper.readTree(extractJsonObject(raw));
        return LlmInterpretationParser.parse(json, today);
    }

    private static IllegalStateException unparseable(String raw, Exception cause) {
        return new IllegalStateException(
                "Klarte ikke å tolke modellsvaret som JSON: " + raw, cause);
    }

    /** Instruksjonen til modellen. Dagens dato gis med, så relative datoer kan regnes ut. */
    private static String systemPrompt(LocalDate today) {
        return """
                Du tolker norske spørsmål om turvær og svarer KUN med ett JSON-objekt,
                uten forklaring og uten kodeblokk-tegn.

                Felter:
                  "region"   - området spørsmålet gjelder: et administrativt område
                               (fylke/kommune/delstat/provins) ELLER en nasjonalpark,
                               hvor som helst i verden. Bruk det offisielle LOKALE
                               navnet slik det skrives i landet selv (f.eks.
                               "Møre og Romsdal", "Stranda", "Jotunheimen nasjonalpark",
                               "Tirol", "Dolomiti Bellunesi"). Ta med "nasjonalpark"
                               (eller landets tilsvarende) i navnet når det er en park.
                               Bruk null hvis det ikke nevnes.
                  "country"  - ISO 3166-1 alpha-2-koden for landet regionen ligger i,
                               f.eks. "NO", "SE", "AT", "IT". Norske steder -> "NO".
                               Bruk null KUN hvis du er usikker på landet.
                  "when"     - tidsuttrykket, NØYAKTIG én av disse kodene:
                               I_DAG, I_MORGEN, I_OVERMORGEN, HELGA, NESTE_HELG,
                               DENNE_UKA, NESTE_UKE, KONKRET, UKJENT.
                               IKKE regn ut datoer selv - velg bare riktig kode.
                               (HELGA = denne helga, NESTE_HELG = helga etter.)
                  "fromDate"/"toDate" - KUN når brukeren nevner en konkret dato
                               (when=KONKRET), ISO YYYY-MM-DD. Ellers null.
                  "target"   - nøyaktig én av:
                               "TUR"    hvis brukeren vil rangere turruter/stier
                                        ("hvilken turrute ... har finest vær"),
                               "VARSEL" hvis brukeren bare spør hvordan været BLIR
                                        på ett sted, uten å be om beste/finest
                                        ("hvordan blir været i Oslo i helga",
                                        "hva slags vær får Bergen i morgen"),
                               "STED"   ellers (rangere steder: "hvor blir det
                                        best/finest vær ...").
                  "tripType" - nøyaktig én av: FJELLTUR, LAVTUR, UANSETT.
                  "weights"  - KUN når spørsmålet uttrykker vær-preferanser, ellers
                               null. Objekt med "temp", "rain", "wind", "elevation",
                               hver nøyaktig LAV, MIDDELS eller HØY.
                               Å HATE/ville unngå noe -> den faktoren HØY (den skal
                               telle mye). At noe «ikke er viktig» -> LAV.
                               Eksempel: «vi hater regn og vind, temperatur er ikke
                               viktig» -> {"temp":"LAV","rain":"HØY","wind":"HØY",
                               "elevation":"MIDDELS"}.

                I dag er %s (tidssone Europe/Oslo) - bruk det bare til å fylle inn
                årstall ved konkrete datoer.

                Eksempler:
                  "hvor blir det best vær i Rogaland neste uke"
                    -> {"region":"Rogaland","country":"NO","when":"NESTE_UKE","target":"STED","fromDate":null,"toDate":null,"tripType":"UANSETT"}
                  "hvilken turrute i Ålesund har finest vær i morgen"
                    -> {"region":"Ålesund","country":"NO","when":"I_MORGEN","target":"TUR","fromDate":null,"toDate":null,"tripType":"UANSETT"}
                  "fint fjellvær i Møre og Romsdal i helga"
                    -> {"region":"Møre og Romsdal","country":"NO","when":"HELGA","target":"STED","fromDate":null,"toDate":null,"tripType":"FJELLTUR"}
                  "hvordan blir været i Oslo i helgen"
                    -> {"region":"Oslo","country":"NO","when":"HELGA","target":"VARSEL","fromDate":null,"toDate":null,"tripType":"UANSETT"}
                  "beste turvær i Tirol neste helg"
                    -> {"region":"Tirol","country":"AT","when":"NESTE_HELG","target":"STED","fromDate":null,"toDate":null,"tripType":"UANSETT"}
                  "været på Sunnmøre 3. juli"
                    -> {"region":"Sunnmøre","country":"NO","when":"KONKRET","target":"STED","fromDate":"%s-07-03","toDate":"%s-07-03","tripType":"UANSETT"}
                  "vi hater regn og vind men temperaturen er ikke viktig, hvor i Nordland bør vi telte i helga"
                    -> {"region":"Nordland","country":"NO","when":"HELGA","target":"STED","fromDate":null,"toDate":null,"tripType":"UANSETT","weights":{"temp":"LAV","rain":"HØY","wind":"HØY","elevation":"MIDDELS"}}
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
