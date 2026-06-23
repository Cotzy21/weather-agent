package no.weatheragent.ranking;

import no.weatheragent.weather.Forecast;
import no.weatheragent.weather.WeatherPoint;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Ren logikk: gjor om et varsel til et dagssammendrag, og et sammendrag
 * til en tallscore. Ingen nettverk - derfor lett aa teste.
 *
 * "Turvaer" bygger paa fire faktorer: varmt, lite nedbor, lite vind og (etter
 * smak) hoyde. Hver faktor har en basis-koeffisient under, og brukeren skrur
 * paa dem via {@link ScoreWeights} (Lav/Middels/Hoy). Hoyde som egen, vektbar
 * faktor erstatter den faste havniva-korreksjonen: vil du ha fjell, vekt hoyde;
 * vil du ha varmt, vekt temperatur (HANDOFF §7 blir et brukervalg).
 */
public final class WeatherScorer {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    /** Vi bryr oss om dagtid for turgaaing, ikke natta. */
    private static final int DAY_START_HOUR = 8;
    private static final int DAY_END_HOUR = 20;

    /** Basis-koeffisienter ved normal vekt (MIDDELS). */
    private static final double TEMP_PER_C = 1.0;    // +1 per grad
    private static final double PRECIP_PER_MM = 3.0; // -3 per mm nedbor
    private static final double WIND_PER_MS = 0.5;   // -0,5 per m/s vind
    private static final double ELEV_PER_M = 0.01;   // +0,01 per meter (1000 m -> +10)

    private WeatherScorer() {
    }

    /**
     * Lag et dagssammendrag for en gitt dato. Tomt resultat hvis varselet
     * ikke dekker den datoen (f.eks. for langt fram i tid).
     */
    public static Optional<DayWeather> summarize(Forecast forecast, LocalDate date) {
        List<WeatherPoint> daytime = forecast.pointsOn(date).stream()
                .filter(WeatherScorer::isDaytime)
                .toList();

        if (daytime.isEmpty()) {
            return Optional.empty();
        }

        double maxTemp = daytime.stream().mapToDouble(WeatherPoint::temperatureC).max().orElse(0);
        double totalPrecip = daytime.stream().mapToDouble(WeatherPoint::precipitationMm).sum();
        double avgWind = daytime.stream().mapToDouble(WeatherPoint::windSpeedMs).average().orElse(0);

        return Optional.of(new DayWeather(
                forecast.location(), date, maxTemp, totalPrecip, avgWind, forecast.elevationMeters()));
    }

    /** Score med normal vekt paa alle faktorer. */
    public static double score(DayWeather day) {
        return score(day, ScoreWeights.DEFAULT);
    }

    /**
     * Hoeyere score = finere turvaer. Hver faktor vektes av brukeren: varmt og
     * hoyt trekker opp, nedbor og vind trekker ned.
     */
    public static double score(DayWeather day, ScoreWeights w) {
        return w.temperature()   * TEMP_PER_C    * day.maxTempC()
             - w.precipitation() * PRECIP_PER_MM * day.totalPrecipMm()
             - w.wind()          * WIND_PER_MS   * day.avgWindMs()
             + w.elevation()     * ELEV_PER_M    * day.elevationMeters();
    }

    /** Samlet score over flere dager med normal vekt. */
    public static double scoreOverPeriod(List<DayWeather> days) {
        return scoreOverPeriod(days, ScoreWeights.DEFAULT);
    }

    /**
     * Samlet score for et sted over flere dager: snittet av dags-scorene.
     * Bare dager med data sendes inn av kalleren, slik at et kortere varsel
     * ikke trekker stedet ned. Tom liste -> 0.
     */
    public static double scoreOverPeriod(List<DayWeather> days, ScoreWeights w) {
        return days.stream().mapToDouble(d -> score(d, w)).average().orElse(0);
    }

    private static boolean isDaytime(WeatherPoint point) {
        int hour = point.time().atZone(OSLO).getHour();
        return hour >= DAY_START_HOUR && hour <= DAY_END_HOUR;
    }
}
