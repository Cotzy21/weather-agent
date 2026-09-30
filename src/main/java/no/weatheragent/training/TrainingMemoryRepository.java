package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TrainingMemoryRepository extends JpaRepository<TrainingMemory, UUID> {
}
