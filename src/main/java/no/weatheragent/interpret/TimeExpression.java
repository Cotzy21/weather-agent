package no.weatheragent.interpret;

import java.util.Locale;

/**
 * Et tidsuttrykk slik LLM-en klassifiserer det. De faktiske datoene regnes ut
 * deterministisk i Java ({@link TimeExpressionResolver}), ALDRI av modellen -
 * dato-aritmetikk er nettopp det små modeller roter med (HANDOFF §6).
 */
public enum TimeExpression {
    I_DAG,
    I_MORGEN,
    I_OVERMORGEN,
    /** Denne helga (kommende/inneværende lørdag-søndag). */
    HELGA,
    NESTE_HELG,
    /** Resten av inneværende uke (i dag til søndag). */
    DENNE_UKA,
    /** Hele uka etter (mandag-søndag). */
    NESTE_UKE,
    /** Bruker nevner en konkret dato; da følger fromDate/toDate med. */
    KONKRET,
    /** Ingen tid nevnt / ikke forstått. */
    UKJENT;

    /** Tolk en JSON-/fritekstverdi, ufølsom for store/små bokstaver. Ukjent -> UKJENT. */
    public static TimeExpression fromString(String value) {
        if (value == null) {
            return UKJENT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UKJENT;
        }
    }
}
