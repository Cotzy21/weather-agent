package no.weatheragent.ranking;

/**
 * Brukerens vekting av de fire faktorene som avgjør «finest turvær».
 * 1.0 = normal vekt; se {@link WeatherScorer} for hvordan de brukes.
 */
public record ScoreWeights(double temperature, double precipitation, double wind, double elevation) {

    /** Alle faktorer like (normal) vekt. */
    public static final ScoreWeights DEFAULT = new ScoreWeights(1, 1, 1, 1);

    /** Bygg vektene fra brukerens Lav/Middels/Høy-valg. */
    public static ScoreWeights of(Impact temperature, Impact precipitation, Impact wind, Impact elevation) {
        return new ScoreWeights(
                temperature.factor(), precipitation.factor(), wind.factor(), elevation.factor());
    }
}
