package no.weatheragent.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.weatheragent.nutrition.FoodFlags.Allergen;
import no.weatheragent.nutrition.FoodFlags.Diet;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Brukerens lagrede kostholdspreferanser (én rad per bruker). Lister lagres
 * som komma-separert tekst og gjøres om til en {@link DietProfile} for bruk.
 */
@Entity
@Table(name = "diet_preferences")
public class DietPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "diet", length = 20, nullable = false)
    private String diet;

    @Column(name = "allergies", length = 300, nullable = false)
    private String allergies;

    @Column(name = "dislikes", length = 1000, nullable = false)
    private String dislikes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DietPreference() {
        // for JPA
    }

    public DietPreference(UUID userId, DietProfile profile) {
        this.userId = userId;
        this.diet = profile.diet().name();
        this.allergies = profile.allergies().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(","));
        this.dislikes = String.join(",", profile.dislikes());
        this.updatedAt = Instant.now();
    }

    public DietProfile toProfile() {
        Set<Allergen> parsedAllergies = split(allergies).stream()
                .filter(FoodFlags.allergenCodes()::contains)
                .map(Allergen::valueOf)
                .collect(Collectors.toSet());
        Diet parsedDiet = Arrays.stream(Diet.values()).anyMatch(d -> d.name().equals(diet))
                ? Diet.valueOf(diet) : Diet.ALT;
        return new DietProfile(parsedDiet, parsedAllergies, split(dislikes));
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public UUID getUserId() { return userId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
