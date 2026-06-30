package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.route.RoutePoint;
import no.weatheragent.routes.SavedRouteService;
import no.weatheragent.web.dto.SaveRouteRequest;
import no.weatheragent.web.dto.SavedRouteDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Innloggede endepunkter. Brukeren identifiseres av Supabase-JWT-ens sub-claim;
 * alt scopes til den brukeren (en kan aldri se/slette en annens ruter).
 */
@RestController
public class UserController {

    private final SavedRouteService routes;

    public UserController(SavedRouteService routes) {
        this.routes = routes;
    }

    /** Hvem er jeg? Beviser at token-valideringen virker. */
    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "id", jwt.getSubject(),
                "email", jwt.getClaimAsString("email"));
    }

    @PostMapping("/api/ruter")
    public SavedRouteDto save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveRouteRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<RoutePoint> geometry = request.geometry().stream()
                .map(p -> new RoutePoint(p.lat(), p.lon()))
                .toList();
        return SavedRouteDto.from(
                routes.save(userId, request.name(), request.distanceKm(), request.ascentM(), geometry));
    }

    @GetMapping("/api/ruter")
    public List<SavedRouteDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return routes.listFor(UUID.fromString(jwt.getSubject())).stream()
                .map(SavedRouteDto::from)
                .toList();
    }

    @DeleteMapping("/api/ruter/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = routes.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
