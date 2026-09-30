package no.weatheragent.training;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExerciseNameMappingRepository extends JpaRepository<ExerciseNameMapping, ExerciseNameMapping.Key> {

    @org.springframework.data.jpa.repository.Query(
            "select m from ExerciseNameMapping m where m.id.userId = :userId and m.id.nameKey in :keys")
    List<ExerciseNameMapping> findForUser(@org.springframework.data.repository.query.Param("userId") UUID userId,
                                          @org.springframework.data.repository.query.Param("keys") Collection<String> keys);
}
