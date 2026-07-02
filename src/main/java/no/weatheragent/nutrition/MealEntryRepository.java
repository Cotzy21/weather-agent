package no.weatheragent.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MealEntryRepository extends JpaRepository<MealEntry, UUID> {

    List<MealEntry> findByUserIdAndDateOrderByCreatedAtAsc(UUID userId, LocalDate date);

    List<MealEntry> findByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
