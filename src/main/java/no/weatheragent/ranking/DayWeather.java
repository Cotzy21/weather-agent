package no.weatheragent.ranking;

import no.weatheragent.geo.Location;

import java.time.LocalDate;

/**
 * Et sammendrag av vaeret for ETT sted paa EN dag (dagtid).
 * Dette er tallene vi rangerer paa.
 *
 * @param maxTempC      hoeyeste temperatur paa dagtid
 * @param totalPrecipMm samlet nedbor paa dagtid
 * @param avgWindMs     gjennomsnittlig vind paa dagtid
 */
public record DayWeather(
        Location location,
        LocalDate date,
        double maxTempC,
        double totalPrecipMm,
        double avgWindMs
) {
}
