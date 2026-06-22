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
 * "Turvaer" defineres her som: varmt, lite nedbor, lite vind. Vektene under
 * kan justeres senere (eller styres av brukeren).
 */
public final class WeatherScorer {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    /** Vi bryr oss om dagtid for turgaaing, ikke natta. */
    private static final int DAY_START_HOUR = 8;
    private static final int DAY_END_HOUR = 20;

    /** Hvor hardt nedbor og vind trekker ned. Hoeyere = strengere. */
    private static final double PRECIP_PENALTY = 3.0;
    private static final double WIND_PENALTY = 0.5;

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

        return Optional.of(new DayWeather(forecast.location(), date, maxTemp, totalPrecip, avgWind));
    }

    /** Hoeyere score = finere turvaer. */
    public static double score(DayWeather day) {
        return day.maxTempC()
                - day.totalPrecipMm() * PRECIP_PENALTY
                - day.avgWindMs() * WIND_PENALTY;
    }

    private static boolean isDaytime(WeatherPoint point) {
        int hour = point.time().atZone(OSLO).getHour();
        return hour >= DAY_START_HOUR && hour <= DAY_END_HOUR;
    }
}
