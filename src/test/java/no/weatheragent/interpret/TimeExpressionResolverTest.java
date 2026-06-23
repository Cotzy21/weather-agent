package no.weatheragent.interpret;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifiserer at tidsuttrykk regnes ut til riktige datoer - deterministisk,
 * uten LLM. Referansedag er tirsdag 2026-06-23 (uke: man 06-22 ... søn 06-28).
 */
class TimeExpressionResolverTest {

    private static final LocalDate TUE = LocalDate.of(2026, 6, 23);

    private static DateRange resolve(TimeExpression when) {
        return TimeExpressionResolver.resolve(when, TUE, null, null);
    }

    @Test
    void today() {
        assertEquals(new DateRange(TUE, TUE), resolve(TimeExpression.I_DAG));
    }

    @Test
    void tomorrow() {
        DateRange r = resolve(TimeExpression.I_MORGEN);
        assertEquals(LocalDate.of(2026, 6, 24), r.from());
        assertEquals(LocalDate.of(2026, 6, 24), r.to());
    }

    @Test
    void thisWeekendIsSaturdaySunday() {
        DateRange r = resolve(TimeExpression.HELGA);
        assertEquals(LocalDate.of(2026, 6, 27), r.from());
        assertEquals(LocalDate.of(2026, 6, 28), r.to());
    }

    @Test
    void nextWeekendIsFollowingSaturdaySunday() {
        DateRange r = resolve(TimeExpression.NESTE_HELG);
        assertEquals(LocalDate.of(2026, 7, 4), r.from());
        assertEquals(LocalDate.of(2026, 7, 5), r.to());
    }

    @Test
    void restOfThisWeekRunsFromTodayToSunday() {
        DateRange r = resolve(TimeExpression.DENNE_UKA);
        assertEquals(TUE, r.from());
        assertEquals(LocalDate.of(2026, 6, 28), r.to());
    }

    @Test
    void nextWeekIsMondayToSunday() {
        DateRange r = resolve(TimeExpression.NESTE_UKE);
        assertEquals(LocalDate.of(2026, 6, 29), r.from());
        assertEquals(LocalDate.of(2026, 7, 5), r.to());
    }

    @Test
    void weekendOnSundayDropsThePastSaturday() {
        LocalDate sunday = LocalDate.of(2026, 6, 28);
        DateRange r = TimeExpressionResolver.resolve(TimeExpression.HELGA, sunday, null, null);
        assertEquals(sunday, r.from());
        assertEquals(sunday, r.to());
    }

    @Test
    void konkretUsesExplicitDates() {
        DateRange r = TimeExpressionResolver.resolve(
                TimeExpression.KONKRET, TUE,
                LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4));
        assertEquals(LocalDate.of(2026, 7, 3), r.from());
        assertEquals(LocalDate.of(2026, 7, 4), r.to());
    }

    @Test
    void unknownWithoutDatesDefaultsToWeekend() {
        DateRange r = resolve(TimeExpression.UKJENT);
        assertEquals(LocalDate.of(2026, 6, 27), r.from());
        assertEquals(LocalDate.of(2026, 6, 28), r.to());
    }
}
