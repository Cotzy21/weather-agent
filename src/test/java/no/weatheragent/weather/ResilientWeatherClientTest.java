package no.weatheragent.weather;

import no.weatheragent.geo.Location;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tester valget mellom MET og Open-Meteo: naar brukes fallbacken, og naar
 * holder MET alene?
 */
class ResilientWeatherClientTest {

    private static final Location SLOGEN = new Location("Slogen", 62.18, 6.86);

    // Et varsel med ett punkt 12. juli 2026 (midt paa dagen UTC).
    private static final Forecast JULY_12 = forecastOn("2026-07-12T10:00:00Z");
    private static final Forecast JULY_5 = forecastOn("2026-07-05T10:00:00Z");

    private final MetWeatherClient met = mock(MetWeatherClient.class);
    private final OpenMeteoWeatherClient openMeteo = mock(OpenMeteoWeatherClient.class);
    private final ResilientWeatherClient client = new ResilientWeatherClient(met, openMeteo);

    @Test
    void brukerMetNaarDenDekkerDatoen() {
        when(met.fetch(SLOGEN)).thenReturn(JULY_5);

        Forecast result = client.fetch(SLOGEN, LocalDate.of(2026, 7, 5));

        assertSame(JULY_5, result);
        verify(openMeteo, never()).fetch(any());
    }

    @Test
    void fallerTilbakeNaarMetIkkeRekkerLangtNok() {
        // MET har bare data til 5. juli, men vi trenger 12. juli ("neste helg").
        when(met.fetch(SLOGEN)).thenReturn(JULY_5);
        when(openMeteo.fetch(SLOGEN)).thenReturn(JULY_12);

        Forecast result = client.fetch(SLOGEN, LocalDate.of(2026, 7, 12));

        assertSame(JULY_12, result);
    }

    @Test
    void fallerTilbakeNaarMetErNede() {
        when(met.fetch(SLOGEN)).thenThrow(new ResourceAccessException("timeout"));
        when(openMeteo.fetch(SLOGEN)).thenReturn(JULY_12);

        Forecast result = client.fetch(SLOGEN, LocalDate.of(2026, 7, 12));

        assertSame(JULY_12, result);
    }

    @Test
    void beholderMetVarseletNaarFallbackenOgsaaFeiler() {
        when(met.fetch(SLOGEN)).thenReturn(JULY_5);
        when(openMeteo.fetch(SLOGEN)).thenThrow(new ResourceAccessException("timeout"));

        Forecast result = client.fetch(SLOGEN, LocalDate.of(2026, 7, 12));

        assertSame(JULY_5, result); // kort varsel er bedre enn ingen
    }

    private static Forecast forecastOn(String instant) {
        return new Forecast(SLOGEN, 1500.0,
                List.of(new WeatherPoint(Instant.parse(instant), 12.0, 0.0, 3.0)));
    }
}
