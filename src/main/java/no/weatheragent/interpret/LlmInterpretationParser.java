package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Oversetter modellens JSON-svar til en {@link Interpretation}. Ren og testbar
 * uten nettverk - akkurat som MetForecastParser/GeocodingResponseParser.
 *
 * Forventet JSON fra modellen:
 * <pre>
 * { "region": "Møre og Romsdal", "when": "NESTE_UKE",
 *   "fromDate": null, "toDate": null, "tripType": "FJELLTUR" }
 * </pre>
 *
 * Modellen klassifiserer bare tidsuttrykket ({@code when}); de faktiske datoene
 * regnes ut i {@link TimeExpressionResolver}. fromDate/toDate brukes bare når
 * brukeren nevner en konkret dato (when=KONKRET).
 *
 * Vi er bevisst tilgivende: små lokale modeller hopper av og til over felter.
 * Ukjent/manglende {@code when} blir UKJENT, og resolveren gir alltid en gyldig
 * {@link DateRange}.
 */
public final class LlmInterpretationParser {

    private LlmInterpretationParser() {
    }

    public static Interpretation parse(JsonNode root, LocalDate today) {
        String region = text(root, "region");
        TimeExpression when = TimeExpression.fromString(text(root, "when"));

        LocalDate from = dateOrNull(root, "fromDate");
        LocalDate to = dateOrNull(root, "toDate");
        DateRange dates = TimeExpressionResolver.resolve(when, today, from, to);

        TripType tripType = TripType.fromString(text(root, "tripType"));

        return new Interpretation(region, when, dates, tripType);
    }

    /** Tekstverdi for et felt, eller null hvis det mangler/er tomt/er JSON-null. */
    private static String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    /** ISO-dato for et felt, eller null hvis det mangler eller ikke lar seg parse. */
    private static LocalDate dateOrNull(JsonNode root, String field) {
        String raw = text(root, field);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
