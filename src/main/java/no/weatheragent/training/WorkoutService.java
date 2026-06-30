package no.weatheragent.training;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Logging og oppslag av treningsøkter. Alt er scoped til eieren (userId fra JWT) -
 * en bruker ser/sletter aldri en annens økter.
 */
@Service
public class WorkoutService {

    private final WorkoutRepository repository;

    public WorkoutService(WorkoutRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Workout log(UUID userId, LocalDate date, String title, List<SetInput> sets) {
        Workout workout = new Workout(userId, date, title);
        for (SetInput set : sets) {
            workout.addSet(set.exercise(), set.reps(), set.weightKg());
        }
        return repository.save(workout);
    }

    @Transactional(readOnly = true)
    public List<Workout> listFor(UUID userId) {
        return repository.findByUserIdWithSets(userId);
    }

    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }

    @Transactional(readOnly = true)
    public List<ProgressRow> progression(UUID userId, String exercise) {
        return repository.progression(userId, exercise);
    }
}
