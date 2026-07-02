package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.nutrition.NutritionFavoriteService;
import no.weatheragent.web.dto.AddFavoriteRequest;
import no.weatheragent.web.dto.FavoriteDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Kosthold-favoritter: hent, legg til og fjern. Alt scopes til innlogget bruker.
 * Nøkkelen er "familie:navn" (samme som frontenden bruker), og slettes via
 * query-param siden den inneholder kolon.
 */
@RestController
public class NutritionController {

    private final NutritionFavoriteService favorites;

    public NutritionController(NutritionFavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping("/api/kosthold/favoritter")
    public List<FavoriteDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return favorites.listFor(UUID.fromString(jwt.getSubject())).stream()
                .map(FavoriteDto::from)
                .toList();
    }

    @PostMapping("/api/kosthold/favoritter")
    public FavoriteDto add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddFavoriteRequest request) {
        return FavoriteDto.from(favorites.add(
                UUID.fromString(jwt.getSubject()),
                request.key(), request.name(), request.emoji(), request.tag()));
    }

    @DeleteMapping("/api/kosthold/favoritter")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @RequestParam("key") String key) {
        boolean deleted = favorites.remove(UUID.fromString(jwt.getSubject()), key);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
