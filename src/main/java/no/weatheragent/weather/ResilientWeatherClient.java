package no.weatheragent.weather;

import no.weatheragent.geo.Location;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;

/**
 * Vaerklienten resten av appen skal bruke: MET foerst, Open-Meteo som fallback.
 *
 * Fallbacken slaar inn i to tilfeller:
 *  1. MET feiler (nede/overbelastet, etter retry i {@link MetWeatherClient}).
 *  2. MET svarer, men varselet dekker ikke den siste datoen vi trenger - MET
 *     stopper paa ~9-10 dager, saa "neste helg" spurt tidlig i uka kan ligge
 *     utenfor. Open-Meteo gir 16 dager. Dette var den kjente feilen der soek
 *     paa helga ga "ingen vaerdata".
 *
 * Vi sjekker dekningen paa DATA (finnes det punkter paa datoen?) i stedet for
 * aa hardkode en horisont - da justerer det seg selv om MET endrer seg.
 */
@Component
public class ResilientWeatherClient {

    private final MetWeatherClient met;
    private final OpenMeteoWeatherClient openMeteo;

    public ResilientWeatherClient(MetWeatherClient met, OpenMeteoWeatherClient openMeteo) {
        this.met = met;
        this.openMeteo = openMeteo;
    }

    /** Varsel uten krav til hvor langt fram det maa rekke. */
    public Forecast fetch(Location location) {
        return fetch(location, null);
    }

    /**
     * Varsel som helst skal dekke {@code lastDateNeeded}. Rekker ikke MET saa
     * langt, proever vi Open-Meteo; feiler ogsaa den, returnerer vi det MET ga
     * oss (bedre med et kort varsel enn ingen).
     */
    public Forecast fetch(Location location, LocalDate lastDateNeeded) {
        Forecast forecast;
        try {
            forecast = met.fetch(location);
        } catch (RestClientException metDown) {
            return openMeteo.fetch(location);
        }

        if (lastDateNeeded != null && forecast.pointsOn(lastDateNeeded).isEmpty()) {
            try {
                return openMeteo.fetch(location);
            } catch (RestClientException fallbackDown) {
                return forecast;
            }
        }
        return forecast;
    }
}
