package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    List<Workout> findByUserIdOrderByDateDescCreatedAtDesc(UUID userId);

    List<Workout> findByUserIdAndTypeOrderByDateAsc(UUID userId, String type);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
