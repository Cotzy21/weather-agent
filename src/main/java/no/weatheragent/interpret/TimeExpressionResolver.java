package no.weatheragent.interpret;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Regner ut faktiske datoer for et {@link TimeExpression}, deterministisk og
 * uten LLM. Ren og testbar.
 *
 * "Uke" følger ISO (mandag-søndag). Fortidige dager droppes - vi klemmer
 * startdatoen til i dag - siden værvarsel bakover i tid er ubrukelig.
 */
public final class TimeExpressionResolver {

    private TimeExpressionResolver() {
    }

    public static DateRange resolve(TimeExpression when, LocalDate today,
                                    LocalDate explicitFrom, LocalDate explicitTo) {
        return switch (when) {
            case I_DAG -> DateRange.single(today);
            case I_MORGEN -> DateRange.single(today.plusDays(1));
            case I_OVERMORGEN -> DateRange.single(today.plusDays(2));
            case HELGA -> thisWeekend(today);
            case NESTE_HELG -> nextWeekend(today);
            case DENNE_UKA -> restOfThisWeek(today);
            case NESTE_UKE -> nextWeek(today);
            case KONKRET -> explicit(today, explicitFrom, explicitTo);
            case UKJENT -> explicitFrom != null
                    ? explicit(today, explicitFrom, explicitTo)
                    : thisWeekend(today); // fornuftig standard for en turvær-app
        };
    }

    /** Lørdag-søndag i inneværende uke, men aldri tidligere enn i dag. */
    private static DateRange thisWeekend(LocalDate today) {
        LocalDate saturday = mondayOf(today).plusDays(5);
        LocalDate sunday = saturday.plusDays(1);
        LocalDate from = today.isAfter(saturday) ? today : saturday;
        return new DateRange(from, sunday);
    }

    /** Lørdag-søndag i uka etter. */
    private static DateRange nextWeekend(LocalDate today) {
        LocalDate saturday = mondayOf(today).plusWeeks(1).plusDays(5);
        return new DateRange(saturday, saturday.plusDays(1));
    }

    /** Fra i dag til og med søndag denne uka. */
    private static DateRange restOfThisWeek(LocalDate today) {
        LocalDate sunday = mondayOf(today).plusDays(6);
        return new DateRange(today, sunday.isBefore(today) ? today : sunday);
    }

    /** Hele uka etter, mandag til søndag. */
    private static DateRange nextWeek(LocalDate today) {
        LocalDate nextMonday = mondayOf(today).plusWeeks(1);
        return new DateRange(nextMonday, nextMonday.plusDays(6));
    }

    /** Eksplisitt oppgitte datoer, med fornuftig oppførsel hvis noe mangler/er snudd. */
    private static DateRange explicit(LocalDate today, LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : today;
        LocalDate t = to != null ? to : f;
        return new DateRange(f, t.isBefore(f) ? f : t);
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
