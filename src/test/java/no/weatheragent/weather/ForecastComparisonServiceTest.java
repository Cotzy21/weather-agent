package no.weatheragent.weather;

import no.weatheragent.geo.Location;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ForecastComparisonServiceTest {
    private final MetWeatherClient met = mock(MetWeatherClient.class);
    private final OpenMeteoWeatherClient open = mock(OpenMeteoWeatherClient.class);
    private final ForecastComparisonService service = new ForecastComparisonService(met, open);

    @Test
    void rejectsUnknownLocationsBeforeCallingProviders() {
        assertThrows(IllegalArgumentException.class, () -> service.compare("https://127.0.0.1"));
        verifyNoInteractions(met, open);
    }

    @Test
    void keepsSourcesSeparateAndLimitsTheWindow() {
        var location = new Location("Ålesund", 62.4722, 6.1495);
        var time = Instant.now().plusSeconds(3600);
        when(met.fetch(any())).thenReturn(new Forecast(location, List.of(
                new WeatherPoint(time.minusSeconds(7200), -4, 0, 1),
                new WeatherPoint(time, 5, 0, 2),
                new WeatherPoint(time.plusSeconds(172800), 8, 0, 3))));
        when(open.fetch(any())).thenReturn(new Forecast(location, List.of(new WeatherPoint(time, 7, 0, 4))));
        var result = service.compare("alesund");
        assertEquals(1, result.met().points().size());
        assertEquals(5, result.met().points().getFirst().temperatureC());
        assertEquals(7, result.openMeteo().points().getFirst().temperatureC());
        verify(met).fetch(location);
        verify(open).fetch(location);
    }

    @Test
    void providerFailureNeverMasqueradesAsAgreement() {
        when(met.fetch(any())).thenThrow(new RestClientException("unavailable"));
        when(open.fetch(any())).thenReturn(new Forecast(new Location("Oslo", 59.9139, 10.7522),
                List.of(new WeatherPoint(Instant.now().plusSeconds(3600), 4, 0, 2))));
        var result = service.compare("oslo");
        assertFalse(result.met().available());
        assertTrue(result.met().points().isEmpty());
        assertTrue(result.openMeteo().available());
    }
}
