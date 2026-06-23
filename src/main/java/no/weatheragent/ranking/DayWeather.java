package no.weatheragent.ranking;

import no.weatheragent.geo.Location;

import java.time.LocalDate;

/**
 * Et sammendrag av vaeret for ETT sted paa EN dag (dagtid).
 * Dette er tallene vi rangerer paa.
 *
 * maxTempC er den EKTE maalte temperaturen (for visning). Scoreren korrigerer
 * den til havniva ved hjelp av elevationMeters (HANDOFF §7).
 *
 * @param maxTempC        hoeyeste temperatur paa dagtid (ekte, ukorrigert)
 * @param totalPrecipMm   samlet nedbor paa dagtid
 * @param avgWindMs       gjennomsnittlig vind paa dagtid
 * @param elevationMeters stedets hoyde over havet (fra MET-varselet)
 */
public record DayWeather(
        Location location,
        LocalDate date,
        double maxTempC,
        double totalPrecipMm,
        double avgWindMs,
        double elevationMeters
) {

    /** Bakoverkompatibel: ukjent hoyde = havniva (0 m). */
    public DayWeather(Location location, LocalDate date,
                      double maxTempC, double totalPrecipMm, double avgWindMs) {
        this(location, date, maxTempC, totalPrecipMm, avgWindMs, 0.0);
    }
}
