package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TrainingProfileRepository extends JpaRepository<TrainingProfile, UUID> {
}
