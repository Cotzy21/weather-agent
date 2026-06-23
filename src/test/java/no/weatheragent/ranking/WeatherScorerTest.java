package no.weatheragent.ranking;

import no.weatheragent.geo.Location;
import no.weatheragent.weather.Forecast;
import no.weatheragent.weather.WeatherPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherScorerTest {

    private static final Location PLACE = new Location("Testby", 62.0, 6.0);
    private static final LocalDate DATE = LocalDate.of(2026, 6, 26); // en fredag

    /** Hjelper: lag et vaerpunkt paa gitt UTC-time den dagen. */
    private static WeatherPoint at(int utcHour, double temp, double precip, double wind) {
        Instant time = Instant.parse(String.format("2026-06-26T%02d:00:00Z", utcHour));
        return new WeatherPoint(time, temp, precip, wind);
    }

    @Test
    void summarizesDaytimeOnly() {
        // 03:00 UTC = 05:00 norsk (natt, skal ignoreres), 10:00 UTC = 12:00 norsk (dagtid)
        Forecast forecast = new Forecast(PLACE, List.of(
                at(3, 30.0, 0.0, 0.0),   // natt - skal IKKE telle, selv om den er varmest
                at(10, 18.0, 0.0, 2.0),  // dag
                at(12, 21.0, 1.0, 4.0)   // dag
        ));

        DayWeather day = WeatherScorer.summarize(forecast, DATE).orElseThrow();

        assertEquals(21.0, day.maxTempC());      // varmeste DAGTID-punkt, ikke natta
        assertEquals(1.0, day.totalPrecipMm());  // sum dagtid
        assertEquals(3.0, day.avgWindMs());      // snitt dagtid (2 + 4) / 2
    }

    @Test
    void returnsEmptyWhenDateNotCovered() {
        Forecast forecast = new Forecast(PLACE, List.of(at(10, 18.0, 0.0, 2.0)));

        Optional<DayWeather> result = WeatherScorer.summarize(forecast, LocalDate.of(2026, 7, 1));

        assertTrue(result.isEmpty());
    }

    @Test
    void warmerAndDrierScoresHigher() {
        DayWeather nice = new DayWeather(PLACE, DATE, 22.0, 0.0, 2.0);
        DayWeather rainy = new DayWeather(PLACE, DATE, 22.0, 5.0, 2.0);
        DayWeather cold = new DayWeather(PLACE, DATE, 12.0, 0.0, 2.0);

        assertTrue(WeatherScorer.score(nice) > WeatherScorer.score(rainy));
        assertTrue(WeatherScorer.score(nice) > WeatherScorer.score(cold));
    }

    @Test
    void periodScoreIsAverageOfDailyScores() {
        DayWeather day1 = new DayWeather(PLACE, DATE, 20.0, 0.0, 0.0);            // score 20
        DayWeather day2 = new DayWeather(PLACE, DATE.plusDays(1), 10.0, 0.0, 0.0); // score 10

        // snitt av 20 og 10 = 15
        assertEquals(15.0, WeatherScorer.scoreOverPeriod(List.of(day1, day2)));
    }

    @Test
    void steadyGoodWeekendBeatsOneGreatOneAwfulDay() {
        // Et sted som er jevnt fint hele helga...
        double steady = WeatherScorer.scoreOverPeriod(List.of(
                new DayWeather(PLACE, DATE, 18.0, 0.0, 1.0),
                new DayWeather(PLACE, DATE.plusDays(1), 18.0, 0.0, 1.0)));

        // ...slår et sted med én strålende og én klissvåt dag (samme snitt-temp).
        double swingy = WeatherScorer.scoreOverPeriod(List.of(
                new DayWeather(PLACE, DATE, 24.0, 0.0, 1.0),
                new DayWeather(PLACE, DATE.plusDays(1), 12.0, 12.0, 1.0)));

        assertTrue(steady > swingy);
    }

    @Test
    void emptyPeriodScoresZero() {
        assertEquals(0.0, WeatherScorer.scoreOverPeriod(List.of()));
    }

    @Test
    void higherPeakWinsWithDefaultWeights() {
        // 84 m kystknaus paa 14°C vs ekte fjell paa 1000 m og 10°C.
        // Med normal hoydevekt vinner fjellet (HANDOFF §7-avveiningen).
        DayWeather molehill = new DayWeather(PLACE, DATE, 14.0, 0.0, 1.0, 84);
        DayWeather realPeak = new DayWeather(PLACE, DATE, 10.0, 0.0, 1.0, 1000);

        assertTrue(WeatherScorer.score(realPeak) > WeatherScorer.score(molehill));
    }

    @Test
    void lowElevationWeightLetsWarmLowlandWin() {
        // Bryr du deg lite om hoyde og mye om varme, skal den varme knausen vinne.
        ScoreWeights warmth = ScoreWeights.of(Impact.HOY, Impact.MIDDELS, Impact.MIDDELS, Impact.LAV);

        DayWeather molehill = new DayWeather(PLACE, DATE, 14.0, 0.0, 1.0, 84);
        DayWeather realPeak = new DayWeather(PLACE, DATE, 10.0, 0.0, 1.0, 1000);

        assertTrue(WeatherScorer.score(molehill, warmth) > WeatherScorer.score(realPeak, warmth));
    }

    @Test
    void higherRainWeightPenalizesMore() {
        DayWeather rainy = new DayWeather(PLACE, DATE, 15.0, 5.0, 0.0, 0);

        ScoreWeights lavRain = ScoreWeights.of(Impact.MIDDELS, Impact.LAV, Impact.MIDDELS, Impact.MIDDELS);
        ScoreWeights hoyRain = ScoreWeights.of(Impact.MIDDELS, Impact.HOY, Impact.MIDDELS, Impact.MIDDELS);

        assertTrue(WeatherScorer.score(rainy, hoyRain) < WeatherScorer.score(rainy, lavRain));
    }

    @Test
    void elevationDoesNotChangeScoreAtSeaLevel() {
        DayWeather atSea = new DayWeather(PLACE, DATE, 18.0, 0.0, 0.0, 0);

        // Ingen hoyde -> hoydeleddet er 0 -> score == maaltemp ved normal vekt.
        assertEquals(18.0, WeatherScorer.score(atSea));
    }
}
