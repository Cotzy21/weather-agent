package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    /** Brukerens økter, nyeste først, med settene hentet (unngår lazy-feil ved mapping). */
    @Query("""
            select distinct w from Workout w
            left join fetch w.sets
            where w.userId = :userId
            order by w.date desc, w.createdAt desc""")
    List<Workout> findByUserIdWithSets(UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);

    /** Progresjon per dag for én øvelse: beste vekt og totalt volum (reps*vekt). */
    @Query("""
            select w.date as date, max(s.weightKg) as maxWeight, sum(s.reps * s.weightKg) as volume
            from ExerciseSet s join s.workout w
            where w.userId = :userId and lower(s.exercise) = lower(:exercise)
            group by w.date
            order by w.date""")
    List<ProgressRow> progression(UUID userId, String exercise);
}
