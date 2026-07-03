package no.weatheragent.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Brukernes egne måltider, scoped til eieren (userId fra JWT). Ingrediensene
 * valideres strukturelt (array med tak) før lagring - aldri stol på
 * klient-JSON, heller ikke størrelsen på den (security-first).
 */
@Service
public class CustomMealService {

    static final int MAX_MEALS = 100;
    static final int MAX_INGREDIENTS = 30;

    private final CustomMealRepository repository;

    public CustomMealService(CustomMealRepository repository) {
        this.repository = repository;
    }

    public List<CustomMeal> listFor(UUID userId) {
        return repository.findByUserIdOrderByCreatedAt(userId);
    }

    @Transactional
    public CustomMeal save(UUID userId, String name, JsonNode ingredients) {
        if (ingredients == null || !ingredients.isArray() || ingredients.isEmpty()) {
            throw new InvalidMealException("Et måltid må ha minst én ingrediens.");
        }
        if (ingredients.size() > MAX_INGREDIENTS) {
            throw new InvalidMealException(
                    "Maks " + MAX_INGREDIENTS + " ingredienser per måltid.");
        }
        if (repository.countByUserId(userId) >= MAX_MEALS) {
            throw new MealLimitException(
                    "Maks " + MAX_MEALS + " måltider - slett noen først.");
        }
        return repository.save(new CustomMeal(userId, name.trim(), ingredients));
    }

    /** Sletter brukerens eget måltid. True hvis noe ble slettet. */
    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }
}
