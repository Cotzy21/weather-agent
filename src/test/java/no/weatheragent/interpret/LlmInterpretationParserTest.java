package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester den rene tolkningen av modell-JSON, uten nettverk eller Spring.
 * today er en tirsdag, så relative tidsuttrykk har faste forventede datoer.
 */
class LlmInterpretationParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate today = LocalDate.of(2026, 6, 23); // tirsdag

    private Interpretation parse(String json) throws Exception {
        return LlmInterpretationParser.parse(mapper.readTree(json), today);
    }

    @Test
    void parsesRegionWhenAndTripType() throws Exception {
        Interpretation r = parse("""
                {"region":"Rogaland","when":"NESTE_UKE",
                 "fromDate":null,"toDate":null,"tripType":"FJELLTUR"}
                """);

        assertEquals("Rogaland", r.region());
        assertTrue(r.hasRegion());
        assertEquals(TimeExpression.NESTE_UKE, r.when());
        assertEquals(TripType.FJELLTUR, r.tripType());
        // NESTE_UKE = mandag-søndag i uka etter
        assertEquals(LocalDate.of(2026, 6, 29), r.dates().from());
        assertEquals(LocalDate.of(2026, 7, 5), r.dates().to());
    }

    @Test
    void helgaResolvesToThisWeekend() throws Exception {
        Interpretation r = parse("{\"region\":\"Møre og Romsdal\",\"when\":\"HELGA\"}");

        assertEquals(LocalDate.of(2026, 6, 27), r.dates().from());
        assertEquals(LocalDate.of(2026, 6, 28), r.dates().to());
    }

    @Test
    void konkretUsesExplicitDates() throws Exception {
        Interpretation r = parse("""
                {"region":"Sunnmøre","when":"KONKRET",
                 "fromDate":"2026-07-03","toDate":"2026-07-04","tripType":"UANSETT"}
                """);

        assertEquals(TimeExpression.KONKRET, r.when());
        assertEquals(LocalDate.of(2026, 7, 3), r.dates().from());
        assertEquals(LocalDate.of(2026, 7, 4), r.dates().to());
    }

    @Test
    void countryIsUppercasedIsoCode() throws Exception {
        assertEquals("AT", parse("{\"region\":\"Tirol\",\"country\":\"at\"}").country());
        assertEquals("NO", parse("{\"region\":\"Rogaland\",\"country\":\"NO\"}").country());
    }

    @Test
    void invalidOrMissingCountryBecomesNull() throws Exception {
        assertNull(parse("{\"region\":\"Tirol\",\"country\":\"Austria\"}").country());
        assertNull(parse("{\"region\":\"Tirol\",\"country\":null}").country());
        assertNull(parse("{\"region\":\"Tirol\"}").country());
    }

    @Test
    void tripTypeIsCaseInsensitiveAndDefaultsToUansett() throws Exception {
        assertEquals(TripType.LAVTUR, parse("{\"tripType\":\"lavtur\"}").tripType());
        assertEquals(TripType.UANSETT, parse("{\"tripType\":\"piknik\"}").tripType());
        assertEquals(TripType.UANSETT, parse("{}").tripType());
    }

    @Test
    void missingWhenBecomesUkjentAndDefaultsToWeekend() throws Exception {
        Interpretation r = parse("{\"region\":\"Lofoten\"}");

        assertEquals(TimeExpression.UKJENT, r.when());
        // UKJENT uten datoer -> fornuftig standard: denne helga
        assertEquals(LocalDate.of(2026, 6, 27), r.dates().from());
        assertEquals(LocalDate.of(2026, 6, 28), r.dates().to());
    }

    @Test
    void unknownWhenTokenBecomesUkjent() throws Exception {
        assertEquals(TimeExpression.UKJENT, parse("{\"when\":\"snart\"}").when());
    }

    @Test
    void missingRegionLeavesHasRegionFalse() throws Exception {
        Interpretation r = parse("{\"when\":\"I_DAG\"}");

        assertNull(r.region());
        assertFalse(r.hasRegion());
    }

    @Test
    void garbledKonkretDateFallsBackToToday() throws Exception {
        Interpretation r = parse("{\"when\":\"KONKRET\",\"fromDate\":\"i morgen\"}");

        assertEquals(today, r.dates().from());
        assertEquals(today, r.dates().to());
    }
}
