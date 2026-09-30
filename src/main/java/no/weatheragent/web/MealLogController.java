package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.nutrition.CalorieGoal;
import no.weatheragent.nutrition.CalorieGoalService;
import no.weatheragent.nutrition.CustomFood;
import no.weatheragent.nutrition.CustomFoodService;
import no.weatheragent.nutrition.DailyBalanceService;
import no.weatheragent.nutrition.DietPreferenceService;
import no.weatheragent.nutrition.DietProfile;
import no.weatheragent.nutrition.FoodFlags;
import no.weatheragent.nutrition.FoodSearchService;
import no.weatheragent.nutrition.MealLogService;
import no.weatheragent.web.dto.CalorieGoalDto;
import no.weatheragent.web.dto.CreateCustomFoodRequest;
import no.weatheragent.web.dto.CustomFoodDto;
import no.weatheragent.web.dto.DaySummaryDto;
import no.weatheragent.web.dto.DietPreferenceDto;
import no.weatheragent.web.dto.FoodDto;
import no.weatheragent.web.dto.LogMealRequest;
import no.weatheragent.web.dto.MealEntryDto;
import no.weatheragent.web.dto.NutrientStatusDto;
import no.weatheragent.web.dto.SaveGoalRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final DailyBalanceService balance;
    private final CalorieGoalService goals;
    private final CustomFoodService customFoods;
    private final DietPreferenceService preferences;

    public MealLogController(FoodSearchService search, MealLogService meals,
                             DailyBalanceService balance, CalorieGoalService goals,
                             CustomFoodService customFoods, DietPreferenceService preferences) {
        this.search = search;
        this.meals = meals;
        this.balance = balance;
        this.goals = goals;
        this.customFoods = customFoods;
        this.preferences = preferences;
    }

    /**
     * Søk i egne + offentlig delte matvarer OG Matvaretabellen, f.eks.
     * GET /api/kosthold/matvarer?sok=brød. Egne/delte kommer først - de er
     * mer spesifikke (en bestemt shake) enn tabellens generiske varer.
     */
    @GetMapping("/api/kosthold/matvarer")
    public List<FoodDto> foods(@AuthenticationPrincipal Jwt jwt, @RequestParam("sok") String query) {
        if (query != null && query.length() > 100) {
            query = query.substring(0, 100);
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        List<FoodDto> out = new ArrayList<>();
        for (CustomFood f : customFoods.search(userId, query)) {
            out.add(FoodDto.from(f.toFoodItem(), f.getOwnerId().equals(userId) ? "EGEN" : "OFFENTLIG"));
        }
        search.search(query).forEach(f -> out.add(FoodDto.from(f)));

        // Tilpass til brukerens allergier/diett/misliker: merk treffene, og legg
        // de med advarsler bakerst (stabil sortering beholder relevansen ellers).
        DietProfile profile = preferences.profileFor(userId);
        if (profile.isEmpty()) {
            return out;
        }
        return out.stream()
                .map(f -> f.withWarnings(FoodFlags.warnings(f.name(), profile)))
                .sorted(Comparator.comparing(f -> !f.warnings().isEmpty()))
                .toList();
    }

    @GetMapping("/api/kosthold/preferanser")
    public DietPreferenceDto myPreferences(@AuthenticationPrincipal Jwt jwt) {
        return DietPreferenceDto.from(preferences.profileFor(UUID.fromString(jwt.getSubject())));
    }

    /** Lagre diett, allergier og «liker ikke» (erstatter alt, én per bruker). */
    @PutMapping("/api/kosthold/preferanser")
    public DietPreferenceDto savePreferences(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody DietPreferenceDto request) {
        return DietPreferenceDto.from(preferences.save(UUID.fromString(jwt.getSubject()),
                request.diet(), request.allergies(), request.dislikes()));
    }

    /** Slå opp en vare på strekkode (egne + offentlig delte), 404 hvis ukjent. */
    @GetMapping("/api/kosthold/strekkode/{kode}")
    public ResponseEntity<FoodDto> byBarcode(@AuthenticationPrincipal Jwt jwt, @PathVariable String kode) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return customFoods.byBarcode(userId, kode)
                .map(f -> ResponseEntity.ok(FoodDto.from(f.toFoodItem(),
                        f.getOwnerId().equals(userId) ? "EGEN" : "OFFENTLIG")))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/kosthold/egne-matvarer")
    public List<CustomFoodDto> myFoods(@AuthenticationPrincipal Jwt jwt) {
        return customFoods.listMine(UUID.fromString(jwt.getSubject())).stream()
                .map(CustomFoodDto::from)
                .toList();
    }

    @PostMapping("/api/kosthold/egne-matvarer")
    public CustomFoodDto createFood(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody CreateCustomFoodRequest r) {
        return CustomFoodDto.from(customFoods.create(UUID.fromString(jwt.getSubject()),
                r.name(), r.brand(), r.barcode(),
                r.kcalPer100g(), r.proteinPer100g(), r.fatPer100g(), r.carbPer100g(),
                r.portionName(), r.portionGrams(), r.isPublic()));
    }

    @DeleteMapping("/api/kosthold/egne-matvarer/{id}")
    public ResponseEntity<Void> deleteFood(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = customFoods.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
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

    /**
     * Dagens dagbok MED balansen mot trening og kalorimål,
     * f.eks. GET /api/kosthold/dag?dato=2026-07-02
     */
    @GetMapping("/api/kosthold/dag")
    public DaySummaryDto day(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam("dato") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return DaySummaryDto.from(balance.day(UUID.fromString(jwt.getSubject()), date));
    }

    /** Kalorimålet, eller 404 til profilen er satt opp. */
    @GetMapping("/api/kosthold/maal")
    public ResponseEntity<CalorieGoalDto> goal(@AuthenticationPrincipal Jwt jwt) {
        return goals.find(UUID.fromString(jwt.getSubject()))
                .map(g -> ResponseEntity.ok(CalorieGoalDto.from(g, goals.dailyTargetKcal(g))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Lagre/oppdatere kalorimål-profilen (én per bruker). */
    @PutMapping("/api/kosthold/maal")
    public CalorieGoalDto saveGoal(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveGoalRequest request) {
        CalorieGoal saved = goals.save(UUID.fromString(jwt.getSubject()),
                request.weightKg(), request.heightCm(), request.age(),
                request.sex().toUpperCase(), request.activityLevel().toUpperCase(),
                request.goalKgPerWeek());
        return CalorieGoalDto.from(saved, goals.dailyTargetKcal(saved));
    }

    /** Ukas næringsstatus (7 dager til og med dato), GET /api/kosthold/uke?til=2026-07-02 */
    @GetMapping("/api/kosthold/uke")
    public List<NutrientStatusDto> week(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam("til") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = UUID.fromString(jwt.getSubject());
        // Rådene («gode kilder: …») tilpasses allergier/diett/misliker.
        return meals.week(userId, endDate, preferences.profileFor(userId)).stream()
                .map(NutrientStatusDto::from)
                .toList();
    }
}
