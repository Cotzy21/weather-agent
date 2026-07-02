package no.weatheragent.web.dto;

import no.weatheragent.nutrition.NutritionFavorite;

/** En kosthold-favoritt slik API-et eksponerer den. */
public record FavoriteDto(String key, String name, String emoji, String tag) {

    public static FavoriteDto from(NutritionFavorite f) {
        return new FavoriteDto(f.getFoodKey(), f.getName(), f.getEmoji(), f.getTag());
    }
}
