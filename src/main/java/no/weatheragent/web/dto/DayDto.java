package no.weatheragent.web.dto;

import no.weatheragent.ranking.DayWeather;

/** Værsammendrag for én dag slik API-et eksponerer det. */
public record DayDto(String date, double maxTempC, double totalPrecipMm, double avgWindMs) {

    public static DayDto from(DayWeather d) {
        return new DayDto(d.date().toString(), d.maxTempC(), d.totalPrecipMm(), d.avgWindMs());
    }
}
