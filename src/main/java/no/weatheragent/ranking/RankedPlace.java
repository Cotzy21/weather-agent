package no.weatheragent.ranking;

/**
 * Et sted med sitt dagssammendrag og den utregnede scoren.
 * En sortert liste av disse er svaret paa "hvor blir det finest".
 */
public record RankedPlace(DayWeather weather, double score) {
}
