package no.weatheragent.web.dto;

import no.weatheragent.assist.PlaceForecast;

import java.util.List;

/** Detaljside for ett sted (/api/sted) slik API-et eksponerer det. */
public record PlaceForecastDto(String name,
                               double elevationM,
                               List<DayDto> days,
                               List<TrailDto> trails,
                               List<String> clothing) {

    public static PlaceForecastDto from(PlaceForecast f) {
        return new PlaceForecastDto(
                f.name(),
                f.elevationMeters(),
                f.days().stream().map(DayDto::from).toList(),
                f.trails().stream().map(TrailDto::from).toList(),
                f.clothing());
    }
}
