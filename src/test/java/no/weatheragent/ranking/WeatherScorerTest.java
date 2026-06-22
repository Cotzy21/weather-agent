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
}
