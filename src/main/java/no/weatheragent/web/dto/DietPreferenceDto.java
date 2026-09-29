package no.weatheragent.web.dto;

import no.weatheragent.nutrition.DietPreferenceService;
import no.weatheragent.nutrition.DietProfile;

import java.util.List;

/**
 * Kostholdspreferanser inn og ut: diett (ALT/VEGETAR/PESCETAR/VEGAN),
 * allergen-koder (GLUTEN, MELK …) og ord for mat man ikke liker.
 */
public record DietPreferenceDto(String diet, List<String> allergies, List<String> dislikes) {

    public static DietPreferenceDto from(DietProfile p) {
        return new DietPreferenceDto(p.diet().name(), DietPreferenceService.codes(p.allergies()), p.dislikes());
    }
}
