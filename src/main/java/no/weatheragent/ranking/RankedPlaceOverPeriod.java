package no.weatheragent.ranking;

import no.weatheragent.geo.Location;

import java.util.List;

/**
 * Et sted vurdert over en hel PERIODE (f.eks. en helg): dagssammendragene for
 * hver dag med data, og en samlet score. Scoren er snittet av dags-scorene
 * (se {@link WeatherScorer#scoreOverPeriod}), så et sted som mangler enkelte
 * dager ikke straffes urettferdig.
 *
 * Snitt-feltene under er bare for visning/forklaring; rangeringen skjer på score.
 */
public record RankedPlaceOverPeriod(Location location, List<DayWeather> days, double score) {

    /** Snitt av høyeste dagtemperatur per dag. */
    public double avgMaxTempC() {
        return days.stream().mapToDouble(DayWeather::maxTempC).average().orElse(0);
    }

    /** Snitt av samlet dagnedbør per dag. */
    public double avgPrecipMm() {
        return days.stream().mapToDouble(DayWeather::totalPrecipMm).average().orElse(0);
    }

    /** Snitt av gjennomsnittsvind per dag. */
    public double avgWindMs() {
        return days.stream().mapToDouble(DayWeather::avgWindMs).average().orElse(0);
    }

    /** Stedets hoyde over havet (lik for alle dagene). 0 hvis ukjent. */
    public double elevationMeters() {
        return days.isEmpty() ? 0 : days.getFirst().elevationMeters();
    }
}
