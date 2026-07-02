package no.weatheragent.nutrition;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kostholdsdagboka: logg matvarer med gram, se dagens totaler, og få en
 * ukesoversikt over vitaminer/mineraler målt mot anbefalt daglig inntak.
 *
 * All næringsberegning skjer lokalt mot den cachede Matvaretabellen -
 * ingen eksterne kall per forespørsel.
 */
@Service
public class MealLogService {

    /** Måltidene dagboka kjenner; alt annet avvises. */
    public static final Set<String> MEALS = Set.of("FROKOST", "LUNSJ", "MIDDAG", "KVELDS", "MELLOM");

    /** Under denne dekningsgraden viser ukesoversikten råd for stoffet. */
    private static final double LOW_THRESHOLD_PERCENT = 75;

    private static final int WEEK_DAYS = 7;

    private final MealEntryRepository repository;
    private final MatvaretabellenClient foods;

    public MealLogService(MealEntryRepository repository, MatvaretabellenClient foods) {
        this.repository = repository;
        this.foods = foods;
    }

    /** Dagens loggede matvarer pluss totaler. */
    public record DaySummary(List<MealEntry> entries,
                             double kcal, double proteinG, double fatG, double carbG) {
    }

    /** Ett næringsstoff i ukesoversikten: snitt per logget dag mot dagsmålet. */
    public record NutrientStatus(NutrientReference reference, double avgPerDay, double percent) {

        /** Råd vises bare når inntaket er lavt - ellers er alt vel. */
        public String advice() {
            return percent < LOW_THRESHOLD_PERCENT ? reference.lowAdvice() : null;
        }
    }

    @Transactional
    public MealEntry log(UUID userId, LocalDate date, String meal, String foodId, double grams) {
        if (!MEALS.contains(meal)) {
            throw new IllegalArgumentException("Ukjent måltid: " + meal);
        }
        FoodItem food = foodById(foodId);

        double factor = grams / 100.0;
        return repository.save(new MealEntry(userId, date, meal, food.foodId(), food.name(), grams,
                food.kcalPer100g() * factor,
                food.nutrient("Protein") * factor,
                food.nutrient("Fett") * factor,
                food.nutrient("Karbo") * factor));
    }

    @Transactional(readOnly = true)
    public DaySummary day(UUID userId, LocalDate date) {
        List<MealEntry> entries = repository.findByUserIdAndDateOrderByCreatedAtAsc(userId, date);
        return new DaySummary(entries,
                entries.stream().mapToDouble(MealEntry::getKcal).sum(),
                entries.stream().mapToDouble(MealEntry::getProteinG).sum(),
                entries.stream().mapToDouble(MealEntry::getFatG).sum(),
                entries.stream().mapToDouble(MealEntry::getCarbG).sum());
    }

    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }

    /**
     * Vitamin-/mineralstatus for de siste 7 dagene til og med {@code endDate}.
     * Snittet regnes per DAG MED LOGG (ikke delt på 7): den som bare har ført
     * tre dager, skal ikke se «mangel» bare fordi resten av uka er tom.
     */
    @Transactional(readOnly = true)
    public List<NutrientStatus> week(UUID userId, LocalDate endDate) {
        List<MealEntry> entries =
                repository.findByUserIdAndDateBetween(userId, endDate.minusDays(WEEK_DAYS - 1), endDate);
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<String, FoodItem> byId = foods.allFoods().stream()
                .collect(Collectors.toMap(FoodItem::foodId, Function.identity(), (a, b) -> a));

        long daysLogged = entries.stream().map(MealEntry::getDate).distinct().count();

        Map<String, Double> totals = new HashMap<>();
        for (MealEntry entry : entries) {
            FoodItem food = byId.get(entry.getFoodId());
            if (food == null) {
                continue; // varen er fjernet fra tabellen - hopp over i stedet for å feile
            }
            double factor = entry.getGrams() / 100.0;
            for (NutrientReference ref : NutrientReference.TRACKED) {
                totals.merge(ref.nutrientId(), food.nutrient(ref.nutrientId()) * factor, Double::sum);
            }
        }

        return NutrientReference.TRACKED.stream()
                .map(ref -> {
                    double avg = totals.getOrDefault(ref.nutrientId(), 0.0) / daysLogged;
                    return new NutrientStatus(ref, avg, ref.percentOfTarget(avg));
                })
                .toList();
    }

    private FoodItem foodById(String foodId) {
        return foods.allFoods().stream()
                .filter(f -> f.foodId().equals(foodId))
                .findFirst()
                .orElseThrow(() -> new UnknownFoodException(foodId));
    }
}
