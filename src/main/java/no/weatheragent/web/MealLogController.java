package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.nutrition.FoodSearchService;
import no.weatheragent.nutrition.MealLogService;
import no.weatheragent.web.dto.DaySummaryDto;
import no.weatheragent.web.dto.FoodDto;
import no.weatheragent.web.dto.LogMealRequest;
import no.weatheragent.web.dto.MealEntryDto;
import no.weatheragent.web.dto.NutrientStatusDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Kostholdsdagboka over REST: søk i Matvaretabellen, logg matvarer med gram,
 * se dagens totaler og ukas vitamin-/mineralstatus. Alt scopes til innlogget
 * bruker (userId = JWT-ens sub), samme mønster som favorittene.
 */
@RestController
public class MealLogController {

    private final FoodSearchService search;
    private final MealLogService meals;

    public MealLogController(FoodSearchService search, MealLogService meals) {
        this.search = search;
        this.meals = meals;
    }

    /** Søk i Matvaretabellen, f.eks. GET /api/kosthold/matvarer?sok=brød */
    @GetMapping("/api/kosthold/matvarer")
    public List<FoodDto> foods(@RequestParam("sok") String query) {
        return search.search(query).stream().map(FoodDto::from).toList();
    }

    @PostMapping("/api/kosthold/logg")
    public MealEntryDto log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogMealRequest request) {
        return MealEntryDto.from(meals.log(
                UUID.fromString(jwt.getSubject()),
                request.date(), request.meal().toUpperCase(), request.foodId(), request.grams()));
    }

    @DeleteMapping("/api/kosthold/logg/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = meals.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Dagens dagbok, f.eks. GET /api/kosthold/dag?dato=2026-07-02 */
    @GetMapping("/api/kosthold/dag")
    public DaySummaryDto day(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam("dato") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return DaySummaryDto.from(meals.day(UUID.fromString(jwt.getSubject()), date));
    }

    /** Ukas næringsstatus (7 dager til og med dato), GET /api/kosthold/uke?til=2026-07-02 */
    @GetMapping("/api/kosthold/uke")
    public List<NutrientStatusDto> week(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam("til") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return meals.week(UUID.fromString(jwt.getSubject()), endDate).stream()
                .map(NutrientStatusDto::from)
                .toList();
    }
}
