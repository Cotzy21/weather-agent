package no.weatheragent.interpret;

import java.time.LocalDate;
import java.util.List;

/**
 * Et inklusivt datointervall [from, to]. "I helga" blir typisk lørdag->søndag,
 * "i morgen" blir ett døgn (from == to).
 *
 * BestWeatherFinder.rank(...) jobber per dag, så {@link #days()} gir lista vi
 * kan loope over når vi kobler tolkningen mot rangeringen senere.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to kan ikke være null");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to (" + to + ") er før from (" + from + ")");
        }
    }

    /** Ett enkelt døgn. */
    public static DateRange single(LocalDate day) {
        return new DateRange(day, day);
    }

    /** Alle dagene i intervallet, inklusivt begge ender. */
    public List<LocalDate> days() {
        return from.datesUntil(to.plusDays(1)).toList();
    }
}
