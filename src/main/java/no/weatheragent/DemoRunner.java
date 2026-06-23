package no.weatheragent;

import no.weatheragent.geo.Location;
import no.weatheragent.hiking.CandidateSelector;
import no.weatheragent.hiking.OverpassClient;
import no.weatheragent.hiking.Peak;
import no.weatheragent.ranking.BestWeatherFinder;
import no.weatheragent.ranking.DayWeather;
import no.weatheragent.ranking.RankedPlace;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Midlertidig demo: finn det fineste turvaeret i et HELT fylke for kommende
 * fredag, basert paa ekte fjelltopper fra OpenStreetMap. Viser hele kjeden
 * fylke -> topper -> kandidater -> vaer -> rangering. Fjernes naar vi far et
 * REST-lag.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    // Tuning: storre rute = faerre kandidater = faerre vaer-oppslag (raskere).
    private static final double CELL_DEGREES = 0.40;
    private static final double MIN_ELEVATION_M = 800;

    private final OverpassClient overpassClient;
    private final BestWeatherFinder bestWeatherFinder;

    public DemoRunner(OverpassClient overpassClient, BestWeatherFinder bestWeatherFinder) {
        this.overpassClient = overpassClient;
        this.bestWeatherFinder = bestWeatherFinder;
    }

    @Override
    public void run(String... args) {
        String county = "Møre og Romsdal";
        LocalDate friday = nextFriday();

        // 1) Hent alle navngitte topper i fylket fra OpenStreetMap.
        List<Peak> peaks = overpassClient.peaksInCounty(county);

        // 2) Reduser til et geografisk spredt kandidatsett.
        List<Location> candidates = CandidateSelector
                .representativePeaks(peaks, CELL_DEGREES, MIN_ELEVATION_M)
                .stream()
                .map(Peak::location)
                .toList();

        System.out.printf("%n=== Finest turvaer i %s %s ===%n", county, friday);
        System.out.printf("%d topper i fylket -> %d kandidater (vaer-oppslag)%n%n",
                peaks.size(), candidates.size());

        // 3) Hent vaer for hver kandidat, score og ranger.
        List<RankedPlace> ranking = bestWeatherFinder.rank(candidates, friday);

        if (ranking.isEmpty()) {
            System.out.println("Ingen vaerdata for den datoen (kanskje for langt fram?).");
            return;
        }

        ranking.stream().limit(10).forEach(rp -> {
            DayWeather w = rp.weather();
            System.out.printf("  %-20s %4.1f°C  %4.1f mm regn  %4.1f m/s vind   (score %.1f)%n",
                    w.location().name(), w.maxTempC(), w.totalPrecipMm(), w.avgWindMs(), rp.score());
        });

        RankedPlace best = ranking.getFirst();
        System.out.printf("%n=> Finest vaer: %s (%.1f°C, %.1f mm regn)%n%n",
                best.weather().location().name(),
                best.weather().maxTempC(), best.weather().totalPrecipMm());
    }

    /** Forstkommende fredag (i dag hvis det allerede er fredag), i norsk lokaltid. */
    private static LocalDate nextFriday() {
        return LocalDate.now(OSLO).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
    }
}
