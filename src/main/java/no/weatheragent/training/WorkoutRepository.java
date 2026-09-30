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

    /** Bare nøklene som trengs for å oppdage duplikater ved import (unngår å laste all JSON-innhold). */
    interface WorkoutKey {
        LocalDate getDate();

        String getTitle();

        String getType();
    }

    @org.springframework.data.jpa.repository.Query(
            "select w.date as date, w.title as title, w.type as type from Workout w "
                    + "where w.userId = :userId and w.date between :from and :to")
    List<WorkoutKey> findKeysBetween(@org.springframework.data.repository.query.Param("userId") UUID userId,
                                     @org.springframework.data.repository.query.Param("from") LocalDate from,
                                     @org.springframework.data.repository.query.Param("to") LocalDate to);

    List<Workout> findByUserIdAndTypeAndDateBetween(UUID userId, String type, LocalDate from, LocalDate to);

    java.util.Optional<Workout> findByUserIdAndClientId(UUID userId, UUID clientId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
