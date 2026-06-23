package no.weatheragent.ranking;

import java.util.Locale;

/**
 * Hvor mye en faktor skal påvirke scoringen - brukerens valg i UI-et.
 * Tallet er en multiplikator på faktorens normale vekt (MIDDELS = 1.0).
 */
public enum Impact {
    LAV(0.5),
    MIDDELS(1.0),
    HOY(2.0);

    private final double factor;

    Impact(double factor) {
        this.factor = factor;
    }

    public double factor() {
        return factor;
    }

    /** Tolk en verdi fra UI-et, ufølsom for store/små bokstaver. Tåler "HØY". Ukjent -> MIDDELS. */
    public static Impact fromString(String value) {
        if (value == null) {
            return MIDDELS;
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace('Ø', 'O');
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            return MIDDELS;
        }
    }
}
