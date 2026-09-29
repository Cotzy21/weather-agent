package no.weatheragent.nutrition;

import no.weatheragent.nutrition.FoodFlags.Allergen;
import no.weatheragent.nutrition.FoodFlags.Diet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Kostholdspreferanser: hent (standard = ingen) og lagre (upsert, én per bruker).
 */
@Service
public class DietPreferenceService {

    static final int MAX_DISLIKES = 30;
    private static final int MAX_DISLIKE_LENGTH = 30;

    private final DietPreferenceRepository repository;

    public DietPreferenceService(DietPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DietProfile profileFor(UUID userId) {
        return repository.findById(userId).map(DietPreference::toProfile).orElse(DietProfile.NONE);
    }

    @Transactional
    public DietProfile save(UUID userId, String diet, Collection<String> allergies, Collection<String> dislikes) {
        Diet parsedDiet;
        try {
            parsedDiet = Diet.valueOf(diet == null ? "ALT" : diet.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ukjent diett: " + diet);
        }

        Set<Allergen> parsedAllergies = new LinkedHashSet<>();
        for (String a : allergies == null ? List.<String>of() : allergies) {
            String code = a.trim().toUpperCase(Locale.ROOT);
            if (!FoodFlags.allergenCodes().contains(code)) {
                throw new IllegalArgumentException("Ukjent allergen: " + a);
            }
            parsedAllergies.add(Allergen.valueOf(code));
        }

        // Små bokstaver, uten komma (som er skilletegn i lagringen), uten duplikater.
        List<String> cleanDislikes = (dislikes == null ? List.<String>of() : dislikes).stream()
                .map(d -> d.replace(",", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .distinct()
                .toList();
        if (cleanDislikes.size() > MAX_DISLIKES) {
            throw new IllegalArgumentException("Maks " + MAX_DISLIKES + " ting du ikke liker.");
        }
        if (cleanDislikes.stream().anyMatch(d -> d.length() > MAX_DISLIKE_LENGTH)) {
            throw new IllegalArgumentException("Hvert ord kan være maks " + MAX_DISLIKE_LENGTH + " tegn.");
        }

        DietProfile profile = new DietProfile(parsedDiet, parsedAllergies, cleanDislikes);
        repository.save(new DietPreference(userId, profile));
        return profile;
    }

    /** Allergen-kodene sortert, for stabile API-svar. */
    public static List<String> codes(Set<Allergen> allergies) {
        return allergies.stream().map(Enum::name).sorted().collect(Collectors.toList());
    }
}
