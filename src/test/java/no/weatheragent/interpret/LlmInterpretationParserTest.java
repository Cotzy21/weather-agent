package no.weatheragent.interpret;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester den rene tolkningen av modell-JSON, uten nettverk eller Spring -
 * samme mønster som GeocodingResponseParserTest.
 */
class LlmInterpretationParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate today = LocalDate.of(2026, 6, 23);

    private Interpretation parse(String json) throws Exception {
        return LlmInterpretationParser.parse(mapper.readTree(json), today);
    }

    @Test
    void parsesFullObject() throws Exception {
        Interpretation r = parse("""
                {"region":"Møre og Romsdal","fromDate":"2026-06-27",
                 "toDate":"2026-06-28","tripType":"FJELLTUR"}
                """);

        assertEquals("Møre og Romsdal", r.region());
        assertTrue(r.hasRegion());
        assertEquals(LocalDate.of(2026, 6, 27), r.dates().from());
        assertEquals(LocalDate.of(2026, 6, 28), r.dates().to());
        assertEquals(2, r.dates().days().size());
        assertEquals(TripType.FJELLTUR, r.tripType());
    }

    @Test
    void tripTypeIsCaseInsensitiveAndDefaultsToUansett() throws Exception {
        assertEquals(TripType.LAVTUR, parse("{\"tripType\":\"lavtur\"}").tripType());
        assertEquals(TripType.UANSETT, parse("{\"tripType\":\"piknik\"}").tripType());
        assertEquals(TripType.UANSETT, parse("{}").tripType());
    }

    @Test
    void missingDatesFallBackToToday() throws Exception {
        Interpretation r = parse("{\"region\":\"Lofoten\"}");

        assertEquals(today, r.dates().from());
        assertEquals(today, r.dates().to());
    }

    @Test
    void onlyFromDateGivesSingleDay() throws Exception {
        Interpretation r = parse("{\"fromDate\":\"2026-07-01\"}");

        assertEquals(LocalDate.of(2026, 7, 1), r.dates().from());
        assertEquals(LocalDate.of(2026, 7, 1), r.dates().to());
    }

    @Test
    void missingRegionLeavesHasRegionFalse() throws Exception {
        Interpretation r = parse("{\"fromDate\":\"2026-07-01\",\"toDate\":\"2026-07-01\"}");

        assertNull(r.region());
        assertFalse(r.hasRegion());
    }

    @Test
    void garbledDateFallsBackInsteadOfThrowing() throws Exception {
        Interpretation r = parse("{\"fromDate\":\"i morgen\",\"toDate\":\"snart\"}");

        assertEquals(today, r.dates().from());
        assertEquals(today, r.dates().to());
    }

    @Test
    void reversedDatesAreClampedSoDateRangeNeverThrows() throws Exception {
        Interpretation r = parse("{\"fromDate\":\"2026-07-05\",\"toDate\":\"2026-07-01\"}");

        assertEquals(LocalDate.of(2026, 7, 5), r.dates().from());
        assertEquals(LocalDate.of(2026, 7, 5), r.dates().to());
    }
}
