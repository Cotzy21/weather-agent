package no.weatheragent.web.dto;

import no.weatheragent.assist.TurResult;
import no.weatheragent.interpret.Interpretation;

import java.util.List;

/**
 * Svaret på et værsøk (/api/turvaer) slik API-et eksponerer det: tolkningen flatet
 * ut til enkle felter, og de rangerte stedene + turer + klær. Mapping fra de
 * interne domene-recordene skjer i {@link #from}.
 */
public record WeatherSearchDto(String region,
                               String country,
                               String when,
                               String target,
                               String from,
                               String to,
                               String tripType,
                               List<PlaceDto> places,
                               List<TrailDto> trails,
                               List<String> clothing) {

    public static WeatherSearchDto from(TurResult result) {
        Interpretation i = result.interpretation();
        return new WeatherSearchDto(
                i.region(),
                i.country(),
                i.when().name(),
                i.target().name(),
                i.dates().from().toString(),
                i.dates().to().toString(),
                i.tripType().name(),
                result.ranking().stream().map(PlaceDto::from).toList(),
                result.trails().stream().map(TrailDto::from).toList(),
                result.clothing());
    }
}
