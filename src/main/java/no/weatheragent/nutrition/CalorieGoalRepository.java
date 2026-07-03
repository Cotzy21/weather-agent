package no.weatheragent.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CalorieGoalRepository extends JpaRepository<CalorieGoal, UUID> {
}
