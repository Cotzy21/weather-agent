package no.weatheragent.interpret;

import java.util.Locale;

/**
 * Hva brukeren vil ha rangert etter vær: steder/topper, eller konkrete turruter.
 * Lar appen svare på både «hvor er det finest vær» og «hvilken turrute har finest
 * vær». LLM-en klassifiserer dette; ukjent/uoppgitt blir STED.
 */
public enum Target {
    /** Steder/topper i området (standard). */
    STED,
    /** Navngitte turruter/stier i området. */
    TUR;

    public static Target fromString(String value) {
        if (value == null) {
            return STED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return STED;
        }
    }
}
