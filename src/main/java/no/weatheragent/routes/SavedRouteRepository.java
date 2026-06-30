package no.weatheragent.routes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedRouteRepository extends JpaRepository<SavedRoute, UUID> {

    List<SavedRoute> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Sletter bare hvis ruta tilhører brukeren. Returnerer antall slettet (0 = ikke din/finnes ikke). */
    long deleteByIdAndUserId(UUID id, UUID userId);
}
