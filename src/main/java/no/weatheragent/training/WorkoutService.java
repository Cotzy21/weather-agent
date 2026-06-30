package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Logging og oppslag av treningsøkter, scoped til eieren (userId fra JWT).
 * Progresjon leses ut av JSONB-innholdet for styrkeøkter (sett/dropsett/supersett).
 */
@Service
public class WorkoutService {

    private static final String STRENGTH = "STYRKE";

    private final WorkoutRepository repository;

    public WorkoutService(WorkoutRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Workout log(UUID userId, LocalDate date, String title, String type, JsonNode content, String notes) {
        return repository.save(new Workout(userId, date, title, type, content, notes));
    }

    @Transactional(readOnly = true)
    public List<Workout> listFor(UUID userId) {
        return repository.findByUserIdOrderByDateDescCreatedAtDesc(userId);
    }

    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }

    /** Progresjon per dag for én øvelse på tvers av styrkeøkter: beste vekt + volum. */
    @Transactional(readOnly = true)
    public List<ProgressPoint> progression(UUID userId, String exercise) {
        String target = exercise.trim().toLowerCase(Locale.ROOT);
        Map<LocalDate, double[]> byDate = new LinkedHashMap<>(); // [maxWeight, volume]

        for (Workout w : repository.findByUserIdAndTypeOrderByDateAsc(userId, STRENGTH)) {
            for (JsonNode set : matchingSets(w.getContent(), target)) {
                double weight = set.path("weightKg").asDouble(0);
                int reps = set.path("reps").asInt(0);
                double[] acc = byDate.computeIfAbsent(w.getDate(), d -> new double[]{0, 0});
                acc[0] = Math.max(acc[0], weight);
                acc[1] += reps * weight;
            }
        }

        return byDate.entrySet().stream()
                .map(e -> new ProgressPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    /** Alle sett-noder ({reps, weightKg}) for øvelser som matcher navnet, uansett blokk-type. */
    private static List<JsonNode> matchingSets(JsonNode content, String target) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode block : content.path("blocks")) {
            if ("superset".equals(block.path("kind").asText(""))) {
                for (JsonNode ex : block.path("exercises")) {
                    if (nameMatches(ex.path("name"), target)) {
                        addAll(result, ex.path("sets"));
                    }
                }
            } else if (nameMatches(block.path("name"), target)) {
                addAll(result, block.path("sets"));   // vanlige sett
                addAll(result, block.path("drops"));  // dropsett
            }
        }
        return result;
    }

    private static boolean nameMatches(JsonNode nameNode, String target) {
        return nameNode.asText("").trim().toLowerCase(Locale.ROOT).equals(target);
    }

    private static void addAll(List<JsonNode> out, JsonNode array) {
        if (array.isArray()) {
            array.forEach(out::add);
        }
    }
}
