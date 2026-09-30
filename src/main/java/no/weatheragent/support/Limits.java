package no.weatheragent.support;

import java.time.LocalDate;
import java.time.ZoneId;

/** Felles grenser for data brukeren sender inn, så ingen kan fylle databasen med rader eller absurde verdier. */
public final class Limits {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private Limits() {
    }

    /** Dato mellom {@code yearsBack} år siden og i morgen (litt slingring for tidssoner). */
    public static void requireRecentDate(LocalDate date, int yearsBack, String what) {
        LocalDate today = LocalDate.now(OSLO);
        if (date == null || date.isAfter(today.plusDays(1)) || date.isBefore(today.minusYears(yearsBack))) {
            throw new IllegalArgumentException("Ugyldig dato for " + what + ".");
        }
    }

    /** Tall som er endelig og innenfor [min, max]. */
    public static void requireRange(double value, double min, double max, String what) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("«" + what + "» må være mellom " + min + " og " + max + ".");
        }
    }
}
