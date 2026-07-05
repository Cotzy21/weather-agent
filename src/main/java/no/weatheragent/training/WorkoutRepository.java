package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    List<Workout> findByUserIdOrderByDateDescCreatedAtDesc(UUID userId);

    /** Kun økter på/etter en dato - for «siste uke først»-lasting på fremsiden. */
    List<Workout> findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(UUID userId, LocalDate since);

    List<Workout> findByUserIdAndDate(UUID userId, LocalDate date);

    List<Workout> findByUserIdAndTypeOrderByDateAsc(UUID userId, String type);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
