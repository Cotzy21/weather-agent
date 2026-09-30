package no.weatheragent.nutrition;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rapportering og moderering av offentlig delte matvarer. En rapport skjuler ikke varen av seg selv (det ville latt
 * hvem som helst fjerne andres varer): en administrator ser rapportene og bestemmer om varen slettes eller
 * rapportene avvises.
 */
@Service
public class FoodReportService {

    static final int MAX_REPORTS_PER_USER = 100;
    static final int ADMIN_LIST_LIMIT = 50;
    private static final int MAX_NOTES_SHOWN = 5;

    /** En rapportert vare slik administratoren ser den. */
    public record ReportedFood(CustomFood food, long reports, Instant latestReportAt,
                               Map<ReportReason, Long> reasons, List<String> notes) {
    }

    private final FoodReportRepository reports;
    private final CustomFoodRepository foods;

    public FoodReportService(FoodReportRepository reports, CustomFoodRepository foods) {
        this.reports = reports;
        this.foods = foods;
    }

    /**
     * Rapporter en offentlig delt vare. Å rapportere samme vare to ganger er ufarlig (ingen ny rapport). Man kan ikke
     * rapportere egne varer eller varer som ikke er delt, og antallet rapporter per bruker er begrenset.
     */
    @Transactional
    public void report(UUID reporterId, UUID foodId, ReportReason reason, String note) {
        if (reason == null) {
            throw new IllegalArgumentException("Velg en grunn til rapporten.");
        }
        CustomFood food = foods.findById(foodId).filter(CustomFood::isPublic)
                .orElseThrow(() -> new IllegalArgumentException("Fant ikke den delte matvaren."));
        if (food.getOwnerId().equals(reporterId)) {
            throw new IllegalArgumentException("Du kan ikke rapportere din egen matvare.");
        }
        if (reports.existsByFoodIdAndReporterId(foodId, reporterId)) {
            return;
        }
        if (reports.countByReporterId(reporterId) >= MAX_REPORTS_PER_USER) {
            throw new IllegalArgumentException("Du har sendt for mange rapporter.");
        }
        reports.save(new FoodReport(foodId, reporterId, reason, cleanNote(note)));
    }

    /** Rapporterte varer, flest rapporter først (maks {@value #ADMIN_LIST_LIMIT}). */
    @Transactional(readOnly = true)
    public List<ReportedFood> listReported() {
        List<FoodReportRepository.Summary> summaries = reports.summarise(PageRequest.of(0, ADMIN_LIST_LIMIT));
        Set<UUID> ids = summaries.stream().map(FoodReportRepository.Summary::getFoodId).collect(Collectors.toSet());
        Map<UUID, CustomFood> foodById = foods.findAllById(ids).stream()
                .collect(Collectors.toMap(CustomFood::getId, Function.identity()));
        Map<UUID, List<FoodReport>> reportsByFood = reports.findByFoodIdIn(ids).stream()
                .collect(Collectors.groupingBy(FoodReport::getFoodId, LinkedHashMap::new, Collectors.toList()));

        return summaries.stream()
                .filter(s -> foodById.containsKey(s.getFoodId()))
                .map(s -> {
                    List<FoodReport> mine = reportsByFood.getOrDefault(s.getFoodId(), List.of());
                    Map<ReportReason, Long> reasons = new EnumMap<>(ReportReason.class);
                    mine.forEach(r -> reasons.merge(r.getReason(), 1L, Long::sum));
                    List<String> notes = mine.stream().map(FoodReport::getNote)
                            .filter(n -> n != null && !n.isBlank()).limit(MAX_NOTES_SHOWN).toList();
                    return new ReportedFood(foodById.get(s.getFoodId()), s.getReports(), s.getLatest(), reasons, notes);
                })
                .toList();
    }

    /** Administrator: slett en delt vare (rapportene følger med). False hvis den ikke finnes. */
    @Transactional
    public boolean deleteFood(UUID foodId) {
        reports.deleteByFoodId(foodId);
        if (!foods.existsById(foodId)) {
            return false;
        }
        foods.deleteById(foodId);
        return true;
    }

    /** Administrator: avvis rapportene og la varen bli. Returnerer antall fjernede rapporter. */
    @Transactional
    public long dismiss(UUID foodId) {
        return reports.deleteByFoodId(foodId);
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}
