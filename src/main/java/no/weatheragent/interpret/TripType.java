package no.weatheragent.interpret;

import java.util.Locale;

/**
 * Hva slags tur brukeren er ute etter. Henger sammen med scoring-nyansen i
 * HANDOFF §7: ren temperatur-scoring favoriserer lave/kystnære turer, så
 * dette feltet lar oss skille fjell fra lavt land senere.
 */
public enum TripType {
    /** Høyt til fjells. */
    FJELLTUR,
    /** Lavtliggende / kystnært. */
    LAVTUR,
    /** Ingen preferanse uttrykt. */
    UANSETT;

    /** Tolk en fritekst-/JSON-verdi, ufølsom for store/små bokstaver. Ukjent -> UANSETT. */
    public static TripType fromString(String value) {
        if (value == null) {
            return UANSETT;
        }
        try {
            return TripType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UANSETT;
        }
    }
}
