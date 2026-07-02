package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    List<TrainingPlan> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
