package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.nutrition.CustomMeal;

import java.util.UUID;

/** Et egendefinert måltid slik API-et eksponerer det. */
public record CustomMealDto(UUID id, String name, JsonNode ingredients) {

    public static CustomMealDto from(CustomMeal m) {
        return new CustomMealDto(m.getId(), m.getName(), m.getIngredients());
    }
}
