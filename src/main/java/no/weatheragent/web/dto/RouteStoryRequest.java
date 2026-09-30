package no.weatheragent.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.weatheragent.route.RouteStoryService.RouteFacts;

import java.util.List;

/**
 * Rutefakta til «Fortell om turen». Klienten sender tallene ruteplanleggeren har regnet ut; alt er avgrenset, så
 * forespørselen ikke kan brukes til å sende store mengder tekst til (betalt) språkmodell.
 */
public record RouteStoryRequest(
        @NotBlank @Size(max = 120) String name,
        @DecimalMin("0") @DecimalMax("1000") double distanceKm,
        @DecimalMin("0") @DecimalMax("20000") double ascentM,
        @DecimalMin("0") @DecimalMax("200") double hours,
        @Min(0) @Max(100000) int calories,
        @Size(max = 40) String difficulty,
        @DecimalMin("-500") @DecimalMax("9000") Double highestPointM,
        @DecimalMin("0") @DecimalMax("100") Double maxGradientPct,
        @Size(max = 10) List<@Size(max = 200) String> challenges,
        @Size(max = 10) List<@Size(max = 120) String> snacks,
        @Pattern(regexp = "nb|en") String lang
) {
    public RouteFacts toFacts() {
        return new RouteFacts(name.trim(), distanceKm, ascentM, hours, calories, difficulty, highestPointM,
                maxGradientPct, challenges, snacks);
    }

    /** Svaret: omtalen som ren tekst. */
    public record Reply(String story) {
    }
}
