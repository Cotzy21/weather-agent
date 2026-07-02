package no.weatheragent.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NutritionFavoriteRepository extends JpaRepository<NutritionFavorite, UUID> {

    List<NutritionFavorite> findByUserIdOrderByCreatedAt(UUID userId);

    Optional<NutritionFavorite> findByUserIdAndFoodKey(UUID userId, String foodKey);

    long countByUserId(UUID userId);

    long deleteByUserIdAndFoodKey(UUID userId, String foodKey);
}
