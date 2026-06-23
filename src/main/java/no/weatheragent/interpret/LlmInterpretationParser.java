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
 * { "region": "Møre og Romsdal", "fromDate": "2026-06-27",
 *   "toDate": "2026-06-28", "tripType": "FJELLTUR" }
 * </pre>
 *
 * Vi er bevisst tilgivende: små lokale modeller hopper av og til over felter
 * eller roter med datoformat. Manglende/ugyldige datoer faller tilbake til
 * {@code today}, og et omvendt intervall klemmes, slik at vi aldri sender en
 * ugyldig {@link DateRange} videre.
 */
public final class LlmInterpretationParser {

    private LlmInterpretationParser() {
    }

    public static Interpretation parse(JsonNode root, LocalDate today) {
        String region = text(root, "region");

        LocalDate from = date(root, "fromDate", today);
        LocalDate to = date(root, "toDate", from);
        if (to.isBefore(from)) {
            to = from;
        }

        TripType tripType = TripType.fromString(text(root, "tripType"));

        return new Interpretation(region, new DateRange(from, to), tripType);
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

    /** ISO-dato for et felt, med fallback hvis den mangler eller ikke lar seg parse. */
    private static LocalDate date(JsonNode root, String field, LocalDate fallback) {
        String raw = text(root, field);
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
