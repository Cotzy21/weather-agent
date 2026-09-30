package no.weatheragent.training;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
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

    /** Tak på sett per forespørsel (50 økter x 60 blokker x 100 sett er i praksis aldri nådd). */
    static final int MAX_SETS_PER_REQUEST = 5000;

    public record FitImportResult(int imported, int merged, int skipped) {
    }

    private final WorkoutRepository repository;

    public WorkoutImportService(WorkoutRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportResult importGarmin(UUID userId, String csv) {
        List<GarminActivity> activities = GarminCsvParser.parse(csv);

        Set<String> seen = new HashSet<>();
        if (!activities.isEmpty()) {
            LocalDate from = activities.stream().map(GarminActivity::date).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate to = activities.stream().map(GarminActivity::date).max(Comparator.naturalOrder()).orElseThrow();
            for (WorkoutRepository.WorkoutKey w : repository.findKeysBetween(userId, from, to)) {
                seen.add(key(w.getDate().toString(), w.getTitle(), w.getType()));
            }
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
        // Totaler fra styrkeøkter (CSV-en har ikke øvelser/vekter); brukes til trend per økttype.
        if (a.totalSets() != null && a.totalSets() > 0) {
            content.put("totalSets", a.totalSets());
        }
        if (a.totalReps() != null && a.totalReps() > 0) {
            content.put("totalReps", a.totalReps());
        }
        if (a.avgHr() != null && a.avgHr() > 0) {
            content.put("avgHr", a.avgHr());
        }
        if (a.maxHr() != null && a.maxHr() > 0) {
            content.put("maxHr", a.maxHr());
        }
        return content;
    }

    private static String key(String date, String title, String type) {
        return date + "|" + title.trim().toLowerCase(Locale.ROOT) + "|" + type.toUpperCase(Locale.ROOT);
    }

    /**
     * Importerer styrkeøkter med øvelser og vekter lest fra FIT-filer. Idempotent på {@code clientId}.
     * Har brukeren allerede en styrkeøkt uten øvelser den dagen (fra CSV-importen), får DEN øvelsene i stedet for
     * at det lages en duplikat; tittelen fra CSV-en (f.eks. «Push») beholdes. Ellers lages en ny økt.
     */
    @Transactional
    public FitImportResult importFit(UUID userId, List<FitSession> sessions) {
        int totalSets = sessions.stream().flatMap(s -> s.blocks().stream()).mapToInt(b -> b.sets().size()).sum();
        if (totalSets > MAX_SETS_PER_REQUEST) {
            throw new IllegalArgumentException("For mange sett i én forespørsel.");
        }
        int imported = 0;
        int merged = 0;
        int skipped = 0;
        for (FitSession s : sessions) {
            no.weatheragent.support.Limits.requireRecentDate(s.date(), 30, "økta");
            if (repository.findByUserIdAndClientId(userId, s.clientId()).isPresent()) {
                skipped++;
                continue;
            }
            Workout target = mergeCandidate(userId, s);
            if (target != null) {
                target.mergeDetails(ExerciseCatalog.annotate(detailed(s, target.getContent())), s.clientId());
                repository.save(target);
                merged++;
            } else {
                String title = s.title() == null || s.title().isBlank() ? "Styrkeøkt" : s.title().trim();
                repository.save(new Workout(userId, s.date(), title, "STYRKE",
                        ExerciseCatalog.annotate(detailed(s, JsonNodeFactory.instance.objectNode())),
                        "Importert fra Garmin (FIT)").withOrigin(s.clientId(), null));
                imported++;
            }
        }
        return new FitImportResult(imported, merged, skipped);
    }

    /** Styrkeøkt samme dag som mangler øvelser (CSV-import) og ikke alt er koblet til en FIT-økt; nærmeste varighet vinner. */
    private Workout mergeCandidate(UUID userId, FitSession s) {
        return repository.findByUserIdAndDate(userId, s.date()).stream()
                .filter(w -> "STYRKE".equals(w.getType()))
                .filter(w -> w.getClientId() == null)
                .filter(w -> !w.getContent().path("blocks").isArray() || w.getContent().path("blocks").isEmpty())
                .min(Comparator.comparingDouble(w -> durationGap(w, s)))
                .orElse(null);
    }

    private static double durationGap(Workout w, FitSession s) {
        double have = w.getContent().path("durationMin").asDouble(-1);
        return have < 0 || s.durationMin() == null ? Double.MAX_VALUE / 2 : Math.abs(have - s.durationMin());
    }

    /** Innholdet til en økt med øvelser: eksisterende felt beholdes, manglende fylles fra FIT-filen. */
    private static com.fasterxml.jackson.databind.JsonNode detailed(FitSession s, com.fasterxml.jackson.databind.JsonNode base) {
        ObjectNode content = base.isObject() ? ((ObjectNode) base).deepCopy() : JsonNodeFactory.instance.objectNode();
        var blocks = content.putArray("blocks");
        for (FitSession.Block b : s.blocks()) {
            ObjectNode block = blocks.addObject();
            block.put("kind", "exercise");
            block.put("name", b.name().trim());
            var sets = block.putArray("sets");
            for (FitSession.SetEntry set : b.sets()) {
                ObjectNode n = sets.addObject();
                n.put("reps", set.reps());
                n.put("weightKg", Math.round(set.weightKg() * 100) / 100.0);
            }
        }
        if (!content.has("durationMin") && s.durationMin() != null && s.durationMin() > 0) {
            content.put("durationMin", Math.round(s.durationMin()));
        }
        if (!content.has("kcal") && s.kcal() != null && s.kcal() > 0) {
            content.put("kcal", Math.round(s.kcal()));
        }
        if (!content.has("avgHr") && s.avgHr() != null && s.avgHr() > 0) {
            content.put("avgHr", s.avgHr());
        }
        if (!content.has("maxHr") && s.maxHr() != null && s.maxHr() > 0) {
            content.put("maxHr", s.maxHr());
        }
        return content;
    }
}
