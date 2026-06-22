package no.weatheragent;

import no.weatheragent.ranking.BestWeatherFinder;
import no.weatheragent.ranking.DayWeather;
import no.weatheragent.ranking.RankedPlace;
import no.weatheragent.region.Region;
import no.weatheragent.region.RegionRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/**
 * Midlertidig demo: rangerer turvaeret i en region for kommende fredag,
 * slik at vi ser hele kjeden region -> vaer -> score -> svar. Fjernes
 * naar vi far et REST-lag.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final RegionRegistry regionRegistry;
    private final BestWeatherFinder bestWeatherFinder;

    public DemoRunner(RegionRegistry regionRegistry, BestWeatherFinder bestWeatherFinder) {
        this.regionRegistry = regionRegistry;
        this.bestWeatherFinder = bestWeatherFinder;
    }

    @Override
    public void run(String... args) {
        String regionName = "Sunnmøre";
        LocalDate friday = nextFriday();

        Optional<Region> region = regionRegistry.find(regionName);
        if (region.isEmpty()) {
            System.out.println("Kjenner ikke regionen '" + regionName + "'. Kjente: "
                    + regionRegistry.regionNames());
            return;
        }

        System.out.printf("%n=== Finest turvaer i %s %s ===%n", regionName, friday);
        List<RankedPlace> ranking = bestWeatherFinder.rankRegion(region.get(), friday);

        if (ranking.isEmpty()) {
            System.out.println("Ingen vaerdata for den datoen (kanskje for langt fram?).");
            return;
        }

        int plass = 1;
        for (RankedPlace rp : ranking) {
            DayWeather w = rp.weather();
            System.out.printf("%d. %-12s  %4.1f°C  %4.1f mm regn  %4.1f m/s vind   (score %.1f)%n",
                    plass++, w.location().name(),
                    w.maxTempC(), w.totalPrecipMm(), w.avgWindMs(), rp.score());
        }

        RankedPlace best = ranking.getFirst();
        System.out.printf("%n=> Finest vaer: %s (%.1f°C, %.1f mm regn)%n%n",
                best.weather().location().name(),
                best.weather().maxTempC(), best.weather().totalPrecipMm());
    }

    /** Forstkommende fredag (i dag hvis det allerede er fredag), i norsk lokaltid. */
    private static LocalDate nextFriday() {
        LocalDate today = LocalDate.now(OSLO);
        return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
    }
}
