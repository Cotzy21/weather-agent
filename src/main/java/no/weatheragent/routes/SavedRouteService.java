package no.weatheragent.routes;

import no.weatheragent.route.RoutePoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lagring av brukernes ruter. Alle operasjoner er scoped til eieren (userId fra
 * JWT) - en bruker kan aldri se eller slette en annens ruter.
 */
@Service
public class SavedRouteService {

    private final SavedRouteRepository repository;

    public SavedRouteService(SavedRouteRepository repository) {
        this.repository = repository;
    }

    public SavedRoute save(UUID userId, String name, double distanceKm, double ascentM, List<RoutePoint> geometry) {
        return repository.save(new SavedRoute(userId, name, distanceKm, ascentM, geometry));
    }

    public List<SavedRoute> listFor(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Sletter brukerens egen rute. True hvis noe ble slettet. */
    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }
}
