package no.weatheragent.habit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitLogRepository extends JpaRepository<HabitLog, UUID> {

    Optional<HabitLog> findByHabitIdAndDate(UUID habitId, LocalDate date);

    List<HabitLog> findByHabitIdInAndDateGreaterThanEqual(List<UUID> habitIds, LocalDate from);

    List<HabitLog> findByHabitIdOrderByDateDesc(UUID habitId);

    void deleteByHabitId(UUID habitId);
}
