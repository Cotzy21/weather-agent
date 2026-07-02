package no.weatheragent.nutrition;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Brukernes kosthold-favoritter. Alle operasjoner er scoped til eieren (userId
 * fra JWT). Et tak på antall favoritter hindrer at en klient kan fylle databasen
 * (security-first: aldri stol på klient-input, heller ikke mengden av den).
 */
@Service
public class NutritionFavoriteService {

    static final int MAX_FAVORITES = 300;

    private final NutritionFavoriteRepository repository;

    public NutritionFavoriteService(NutritionFavoriteRepository repository) {
        this.repository = repository;
    }

    public List<NutritionFavorite> listFor(UUID userId) {
        return repository.findByUserIdOrderByCreatedAt(userId);
    }

    /**
     * Legg til en favoritt. Idempotent: finnes den fra før returneres den
     * eksisterende raden i stedet for å feile på unik-indeksen.
     */
    @Transactional
    public NutritionFavorite add(UUID userId, String foodKey, String name, String emoji, String tag) {
        return repository.findByUserIdAndFoodKey(userId, foodKey)
                .orElseGet(() -> {
                    if (repository.countByUserId(userId) >= MAX_FAVORITES) {
                        throw new FavoriteLimitException(
                                "Maks " + MAX_FAVORITES + " favoritter - fjern noen først.");
                    }
                    return repository.save(new NutritionFavorite(userId, foodKey, name, emoji, tag));
                });
    }

    /** Fjern brukerens egen favoritt. True hvis noe ble slettet. */
    @Transactional
    public boolean remove(UUID userId, String foodKey) {
        return repository.deleteByUserIdAndFoodKey(userId, foodKey) > 0;
    }
}
