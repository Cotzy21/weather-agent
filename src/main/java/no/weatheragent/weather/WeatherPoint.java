package no.weatheragent.weather;

import java.time.Instant;

/**
 * Vaeret paa ett tidspunkt. Dette er VAART rene domeneobjekt - ikke
 * MET sitt raa JSON-format. Resten av appen forholder seg bare til dette.
 *
 * @param time          tidspunktet (UTC)
 * @param temperatureC  lufttemperatur i grader celsius
 * @param precipitationMm forventet nedbor neste time (mm)
 * @param windSpeedMs   vindhastighet i meter per sekund
 */
public record WeatherPoint(
        Instant time,
        double temperatureC,
        double precipitationMm,
        double windSpeedMs
) {
}
