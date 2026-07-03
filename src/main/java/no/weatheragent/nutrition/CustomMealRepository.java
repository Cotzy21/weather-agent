package no.weatheragent.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomMealRepository extends JpaRepository<CustomMeal, UUID> {

    List<CustomMeal> findByUserIdOrderByCreatedAt(UUID userId);

    long countByUserId(UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
