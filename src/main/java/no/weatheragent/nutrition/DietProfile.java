package no.weatheragent.nutrition;

import no.weatheragent.nutrition.FoodFlags.Allergen;
import no.weatheragent.nutrition.FoodFlags.Diet;

import java.util.List;
import java.util.Set;

/**
 * Brukerens kostholdspreferanser i ren form (uten JPA): diett, allergier og
 * ord for mat man ikke liker. {@link #NONE} = ingen preferanser satt.
 */
public record DietProfile(Diet diet, Set<Allergen> allergies, List<String> dislikes) {

    public static final DietProfile NONE = new DietProfile(Diet.ALT, Set.of(), List.of());

    public DietProfile {
        allergies = Set.copyOf(allergies);
        dislikes = List.copyOf(dislikes);
    }

    public boolean isEmpty() {
        return diet == Diet.ALT && allergies.isEmpty() && dislikes.isEmpty();
    }
}
