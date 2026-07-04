package no.weatheragent.recovery;

/**
 * Et skadeforebyggings-varsel for én aktivitetstype: treningsmengden har økt
 * raskere enn kroppen rekker å tilpasse seg.
 *
 * @param type               økt-typen (LØPING, STYRKE, ...)
 * @param level              hvor alvorlig: NY_AKTIVITET (ingen historikk å
 *                           sammenligne med), MODERAT eller HOY
 * @param percentAboveNormal hvor mye siste 7 dager ligger over vanlig ukesnivå
 *                           (0 for NY_AKTIVITET - det finnes ikke noe «vanlig»)
 * @param message            typespesifikt råd (hardkodet)
 */
public record InjuryWarning(String type, Level level, int percentAboveNormal, String message) {

    public enum Level {
        NY_AKTIVITET,
        MODERAT,
        HOY
    }
}
