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

    /** Tak per bruker: hver rute kan ha 2000 punkter. */
    static final int MAX_ROUTES = 100;

    public SavedRoute save(UUID userId, String name, double distanceKm, double ascentM, List<RoutePoint> geometry) {
        no.weatheragent.support.Limits.requireRange(distanceKm, 0, 100_000, "distanse");
        no.weatheragent.support.Limits.requireRange(ascentM, 0, 100_000, "stigning");
        for (RoutePoint p : geometry) {
            no.weatheragent.support.Limits.requireRange(p.lat(), -90, 90, "breddegrad");
            no.weatheragent.support.Limits.requireRange(p.lon(), -180, 180, "lengdegrad");
        }
        if (repository.countByUserId(userId) >= MAX_ROUTES) {
            throw new IllegalArgumentException("Du har allerede " + MAX_ROUTES + " ruter - slett noen først.");
        }
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
