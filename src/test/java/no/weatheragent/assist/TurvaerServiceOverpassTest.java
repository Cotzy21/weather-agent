package no.weatheragent.assist;

import no.weatheragent.geo.Location;
import no.weatheragent.geo.OpenMeteoGeocodingClient;
import no.weatheragent.hiking.OverpassClient;
import no.weatheragent.hiking.OverpassUnavailableException;
import no.weatheragent.interpret.DateRange;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.interpret.QueryInterpreter;
import no.weatheragent.interpret.Target;
import no.weatheragent.interpret.TimeExpression;
import no.weatheragent.interpret.TripType;
import no.weatheragent.ranking.BestWeatherFinder;
import no.weatheragent.ranking.DayWeather;
import no.weatheragent.ranking.RankedPlaceOverPeriod;
import no.weatheragent.weather.Forecast;
import no.weatheragent.weather.ResilientWeatherClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Når Overpass er travel: turruter er et tillegg (værsvaret vises uten), mens topper/ruter til selve rangeringen er nødvendige. */
class TurvaerServiceOverpassTest {

    private static final Location OSLO = new Location("Oslo", 59.9, 10.7);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private final QueryInterpreter interpreter = mock(QueryInterpreter.class);
    private final OverpassClient overpass = mock(OverpassClient.class);
    private final BestWeatherFinder finder = mock(BestWeatherFinder.class);
    private final ResilientWeatherClient weather = mock(ResilientWeatherClient.class);
    private final OpenMeteoGeocodingClient geocoding = mock(OpenMeteoGeocodingClient.class);
    private final TurvaerService service = new TurvaerService(interpreter, overpass, finder, weather, geocoding);

    private Interpretation interpretation(Target target) {
        return new Interpretation("Oslo", "NO", TimeExpression.I_MORGEN, target, DateRange.single(DAY), TripType.UANSETT);
    }

    private static RankedPlaceOverPeriod rankedOslo() {
        DayWeather day = new DayWeather(OSLO, DAY, 18.0, 0.0, 3.0, 11.0);
        return new RankedPlaceOverPeriod(OSLO, List.of(day), 10.0);
    }

    @Test
    void aBusyOverpassStillGivesTheWeatherAnswerJustWithoutTrails() {
        when(interpreter.interpret(anyString())).thenReturn(interpretation(Target.VARSEL));
        when(geocoding.findFirst("Oslo")).thenReturn(Optional.of(OSLO));
        when(finder.rankOverPeriod(any(), any(), any())).thenReturn(List.of(rankedOslo()));
        when(overpass.trailsNear(any(), anyInt())).thenThrow(new OverpassUnavailableException(null));

        TurResult result = service.finnBesteVaer("hvordan blir været i Oslo i morgen");

        assertTrue(result.hasAnswer());
        assertEquals("Oslo", result.ranking().getFirst().location().name());
        assertTrue(result.trails().isEmpty());
        assertTrue(!result.clothing().isEmpty()); // klesrådene kommer fra været og påvirkes ikke
    }

    @Test
    void whenThePeaksNeededForTheRankingAreMissingTheUserGetsTheClearErrorInsteadOfAFakeAnswer() {
        when(interpreter.interpret(anyString())).thenReturn(interpretation(Target.STED));
        when(overpass.peaksInArea(anyString(), anyString())).thenThrow(new OverpassUnavailableException(null));

        assertThrows(OverpassUnavailableException.class, () -> service.finnBesteVaer("finest vær i Oslo"));
    }

    @Test
    void thePlaceDetailPageStillOpensWhenTrailsCannotBeFetched() {
        when(weather.fetch(any())).thenReturn(new Forecast(OSLO, 11.0, List.of()));
        when(overpass.trailGeometriesNear(any(), anyInt())).thenThrow(new OverpassUnavailableException(null));

        PlaceForecast detail = service.placeDetail("Oslo", 59.9, 10.7);

        assertEquals("Oslo", detail.name());
        assertEquals(11.0, detail.elevationMeters());
        assertTrue(detail.trails().isEmpty());
    }
}
