package no.weatheragent.assist;

import no.weatheragent.geo.Location;
import no.weatheragent.hiking.Trail;
import no.weatheragent.interpret.DateRange;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.interpret.TimeExpression;
import no.weatheragent.interpret.TripType;
import no.weatheragent.ranking.DayWeather;
import no.weatheragent.ranking.RankedPlaceOverPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TurResultPrinterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 27);

    @Test
    void reportsWhenNoRegionWasFound() {
        Interpretation noRegion = new Interpretation(
                null, TimeExpression.UKJENT, DateRange.single(DAY), TripType.UANSETT);

        String out = TurResultPrinter.format(new TurResult(noRegion, List.of(), List.of(), List.of()));

        assertTrue(out.contains("(ukjent)"));
        assertTrue(out.contains("Fant ingen region"));
    }

    @Test
    void showsWinnerWithNameAndElevation() {
        Interpretation tolkning = new Interpretation(
                "Møre og Romsdal", TimeExpression.HELGA,
                new DateRange(DAY, DAY.plusDays(1)), TripType.FJELLTUR);

        RankedPlaceOverPeriod slogen = new RankedPlaceOverPeriod(
                new Location("Slogen", 62.1, 6.8),
                List.of(new DayWeather(new Location("Slogen", 62.1, 6.8), DAY, 9.0, 0.0, 3.0, 1564)),
                25.0);

        Trail trail = new Trail("Slogen via Patchellhytta", "rwn", "Den Norske Turistforening", 62.1, 6.8);

        String out = TurResultPrinter.format(
                new TurResult(tolkning, List.of(slogen), List.of(trail), List.of("Gode tursko og nok drikke")));

        assertTrue(out.contains("Slogen"), out);
        assertTrue(out.contains("1564 moh"), out);
        assertTrue(out.contains("Finest vær"), out);
        assertTrue(out.contains("Merkede turer i området"), out);
        assertTrue(out.contains("Slogen via Patchellhytta"), out);
        assertTrue(out.contains("Klær & utstyr"), out);
    }
}
