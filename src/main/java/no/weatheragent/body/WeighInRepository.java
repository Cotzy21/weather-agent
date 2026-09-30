package no.weatheragent.body;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeighInRepository extends JpaRepository<WeighIn, UUID> {

    List<WeighIn> findByUserIdOrderByDateAsc(UUID userId);

    Optional<WeighIn> findByUserIdAndDate(UUID userId, LocalDate date);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
