package no.weatheragent.training;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Importerer Garmin-aktiviteter som treningsøkter, scoped til brukeren.
 * Idempotent: en økt med samme dato + tittel + type som en eksisterende
 * hoppes over, så samme CSV kan lastes opp flere ganger uten duplikater.
 * Garmin-kaloriene legges i innholdet ({@code kcal}) og brukes da som
 * fasit i dagsbalansen i stedet for vårt grove estimat.
 */
@Service
public class WorkoutImportService {

    /** Tak på antall rader per import - mot både uhell og misbruk. */
    static final int MAX_ROWS = 2000;

    public record ImportResult(int imported, int skipped) {
    }

    private final WorkoutRepository repository;

    public WorkoutImportService(WorkoutRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportResult importGarmin(UUID userId, String csv) {
        List<GarminActivity> activities = GarminCsvParser.parse(csv);

        Set<String> seen = new HashSet<>();
        for (Workout w : repository.findByUserIdOrderByDateDescCreatedAtDesc(userId)) {
            seen.add(key(w.getDate().toString(), w.getTitle(), w.getType()));
        }

        int imported = 0;
        int skipped = 0;
        for (GarminActivity a : activities) {
            if (imported >= MAX_ROWS) {
                skipped++;
                continue;
            }
            if (!seen.add(key(a.date().toString(), a.title(), a.type()))) {
                skipped++;
                continue;
            }
            repository.save(new Workout(userId, a.date(), a.title(), a.type(),
                    content(a), "Importert fra Garmin"));
            imported++;
        }
        return new ImportResult(imported, skipped);
    }

    private static ObjectNode content(GarminActivity a) {
        ObjectNode content = JsonNodeFactory.instance.objectNode();
        if (a.distanceKm() != null && a.distanceKm() > 0) {
            content.put("distanceKm", Math.round(a.distanceKm() * 100) / 100.0);
        }
        if (a.durationMin() != null && a.durationMin() > 0) {
            content.put("durationMin", Math.round(a.durationMin()));
        }
        if (a.ascentM() != null && a.ascentM() > 0) {
            content.put("ascentM", Math.round(a.ascentM()));
        }
        if (a.kcal() != null && a.kcal() > 0) {
            content.put("kcal", Math.round(a.kcal()));
        }
        return content;
    }

    private static String key(String date, String title, String type) {
        return date + "|" + title.trim().toLowerCase(Locale.ROOT) + "|" + type.toUpperCase(Locale.ROOT);
    }
}
