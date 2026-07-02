package no.weatheragent.interpret;

import java.util.Locale;

/**
 * Hva brukeren egentlig spør om: en rangering av steder/topper, en rangering av
 * turruter, ELLER bare værvarselet for ett sted («hvordan blir været i Oslo i
 * helga»). LLM-en klassifiserer dette; ukjent/uoppgitt blir STED.
 */
public enum Target {
    /** Steder/topper i området rangert etter vær (standard). */
    STED,
    /** Navngitte turruter/stier i området rangert etter vær. */
    TUR,
    /** Bare værvarselet for stedet – ingen rangering. */
    VARSEL;

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
