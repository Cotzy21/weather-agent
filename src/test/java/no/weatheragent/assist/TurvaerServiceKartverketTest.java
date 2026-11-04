package no.weatheragent.assist;

import no.weatheragent.geo.*;
import no.weatheragent.hiking.*;
import no.weatheragent.interpret.*;
import no.weatheragent.ranking.*;
import no.weatheragent.weather.ResilientWeatherClient;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TurvaerServiceKartverketTest {
    private final QueryInterpreter interpreter = mock(QueryInterpreter.class);
    private final OverpassClient overpass = mock(OverpassClient.class);
    private final KartverketClient national = mock(KartverketClient.class);
    private final BestWeatherFinder finder = mock(BestWeatherFinder.class);
    private final ResilientWeatherClient weather = mock(ResilientWeatherClient.class);
    private final OpenMeteoGeocodingClient geocoding = mock(OpenMeteoGeocodingClient.class);
    private final TurvaerService service = new TurvaerService(interpreter, overpass, finder, weather, geocoding, national);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 15);
    private static final Location SUMMIT = new Location("Testtoppen", 59, 6);
    private void query(Target target) {
        when(interpreter.interpret(anyString())).thenReturn(new Interpretation("Rogaland", "NO", TimeExpression.I_MORGEN,
                target, DateRange.single(DAY), TripType.FJELLTUR));
    }
    @Test void nationalSummitsAndOptionalTrailsDoNotTouchOverpass() {
        query(Target.STED);
        when(national.peaksInArea("Rogaland", "NO")).thenReturn(Optional.of(List.of(new Peak(SUMMIT, 900.0))));
        when(national.covers(SUMMIT)).thenReturn(true);
        when(finder.rankOverPeriod(eq(List.of(SUMMIT)), any(), any())).thenReturn(List.of(new RankedPlaceOverPeriod(SUMMIT,
                List.of(new DayWeather(SUMMIT, DAY, 10, 0, 2, 900)), 10)));
        when(national.trailsNear(SUMMIT, 8000)).thenThrow(new KartverketUnavailableException(null));
        var result = service.finnBesteVaer("beste fjellvær i Rogaland");
        assertTrue(result.hasAnswer()); assertTrue(result.trails().isEmpty());
        verifyNoInteractions(overpass);
    }
    @Test void nationalRegionalWalkingPathsDoNotTouchOverpass() {
        query(Target.TUR);
        when(national.trailsInArea("Rogaland", "NO")).thenReturn(Optional.of(List.of(new Trail("Kongeveien", "", "", 59, 6))));
        when(finder.rankOverPeriod(any(), any(), any())).thenReturn(List.of());
        service.finnBesteVaer("beste turvær i Rogaland");
        verify(finder).rankOverPeriod(eq(List.of(new Location("Kongeveien", 59, 6))), any(), any());
        verifyNoInteractions(overpass);
    }
    @Test void unsupportedRegionsKeepTheExistingGlobalProvider() {
        query(Target.STED);
        when(national.peaksInArea("Rogaland", "NO")).thenReturn(Optional.empty());
        when(overpass.peaksInArea("Rogaland", "NO")).thenReturn(List.of());
        when(finder.rankOverPeriod(any(), any(), any())).thenReturn(List.of());
        service.finnBesteVaer("fjellvær");
        verify(overpass).peaksInArea("Rogaland", "NO");
    }
    @Test void anEssentialNationalFailureIsNotReportedAsNoResults() {
        query(Target.STED);
        when(national.peaksInArea("Rogaland", "NO")).thenThrow(new KartverketUnavailableException(null));
        assertThrows(KartverketUnavailableException.class, () -> service.finnBesteVaer("fjellvær"));
        verifyNoInteractions(finder, overpass);
    }
}
