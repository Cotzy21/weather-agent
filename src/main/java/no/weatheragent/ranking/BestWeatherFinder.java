package no.weatheragent.ranking;

import no.weatheragent.geo.Location;
import no.weatheragent.region.Region;
import no.weatheragent.weather.Forecast;
import no.weatheragent.weather.MetWeatherClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Knytter alt sammen: for hvert sted henter den vaeret, lager et
 * dagssammendrag, regner ut score, og sorterer best foerst.
 *
 * Dette er det foerste laget som faktisk gjor I/O (nettverk via vaerklienten),
 * mens selve regnestykket ligger i {@link WeatherScorer} (ren og testbar).
 */
@Component
public class BestWeatherFinder {

    private final MetWeatherClient weatherClient;

    public BestWeatherFinder(MetWeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    /** Ranger alle stedene i en region for en gitt dato, best vaer foerst. */
    public List<RankedPlace> rankRegion(Region region, LocalDate date) {
        return rank(region.places(), date);
    }

    /** Ranger en vilkaarlig liste steder for en gitt dato, best vaer foerst. */
    public List<RankedPlace> rank(List<Location> places, LocalDate date) {
        return places.stream()
                .map(weatherClient::fetch)                       // sted -> varsel
                .map(forecast -> toRankedPlace(forecast, date))  // varsel -> score
                .flatMap(Optional::stream)                       // dropp steder uten data
                .sorted(Comparator.comparingDouble(RankedPlace::score).reversed())
                .toList();
    }

    private Optional<RankedPlace> toRankedPlace(Forecast forecast, LocalDate date) {
        return WeatherScorer.summarize(forecast, date)
                .map(day -> new RankedPlace(day, WeatherScorer.score(day)));
    }

    /** Ranger steder over en periode med normal vekt paa alle faktorer. */
    public List<RankedPlaceOverPeriod> rankOverPeriod(List<Location> places, List<LocalDate> dates) {
        return rankOverPeriod(places, dates, ScoreWeights.DEFAULT);
    }

    /**
     * Ranger steder over en hel PERIODE, best snitt-vaer foerst, med brukerens
     * vekter. Henter varselet EN gang per sted (det dekker mange dager) og
     * scorer paa tvers av dagene.
     */
    public List<RankedPlaceOverPeriod> rankOverPeriod(List<Location> places, List<LocalDate> dates,
                                                      ScoreWeights weights) {
        return places.stream()
                .map(weatherClient::fetch)
                .map(forecast -> toRankedPeriod(forecast, dates, weights))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(RankedPlaceOverPeriod::score).reversed())
                .toList();
    }

    private static Optional<RankedPlaceOverPeriod> toRankedPeriod(Forecast forecast, List<LocalDate> dates,
                                                                  ScoreWeights weights) {
        List<DayWeather> days = dates.stream()
                .map(date -> WeatherScorer.summarize(forecast, date))
                .flatMap(Optional::stream)
                .toList();

        if (days.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RankedPlaceOverPeriod(
                forecast.location(), days, WeatherScorer.scoreOverPeriod(days, weights)));
    }
}
