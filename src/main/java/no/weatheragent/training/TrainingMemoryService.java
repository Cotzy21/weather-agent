package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Treningsminnet: det brukeren har sagt (liker / liker ikke / notater) pluss
 * vaner regnet ut fra øktene. Brukes av AI-forslagene så de blir personlige.
 */
@Service
public class TrainingMemoryService {

    static final int MAX_EXERCISES = 40;
    static final int MAX_NAME_LENGTH = 40;
    static final int MAX_NOTES_LENGTH = 500;
    private static final int TOP_EXERCISES = 8;

    private static final Map<DayOfWeek, String> NORWEGIAN_DAYS = Map.of(
            DayOfWeek.MONDAY, "mandag", DayOfWeek.TUESDAY, "tirsdag", DayOfWeek.WEDNESDAY, "onsdag",
            DayOfWeek.THURSDAY, "torsdag", DayOfWeek.FRIDAY, "fredag", DayOfWeek.SATURDAY, "lørdag",
            DayOfWeek.SUNDAY, "søndag");

    private final TrainingMemoryRepository memories;
    private final WorkoutRepository workouts;

    public TrainingMemoryService(TrainingMemoryRepository memories, WorkoutRepository workouts) {
        this.memories = memories;
        this.workouts = workouts;
    }

    @Transactional(readOnly = true)
    public TrainingMemoryProfile profile(UUID userId, LocalDate today) {
        TrainingMemory memory = memories.findById(userId).orElse(null);
        List<Workout> recent = workouts.findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(
                userId, today.minusDays(89));

        int last28 = (int) recent.stream().filter(w -> !w.getDate().isBefore(today.minusDays(27))).count();
        double avgPerWeek = Math.round(last28 / 4.0 * 10) / 10.0;

        return new TrainingMemoryProfile(
                memory == null ? List.of() : memory.liked(),
                memory == null ? List.of() : memory.disliked(),
                memory == null ? "" : memory.notes(),
                last28,
                avgPerWeek,
                usualDays(recent, today),
                topExercises(recent),
                recent.isEmpty() ? null : recent.get(0).getDate());
    }

    @Transactional
    public void save(UUID userId, Collection<String> liked, Collection<String> disliked, String notes) {
        List<String> cleanLiked = clean(liked);
        List<String> cleanDisliked = clean(disliked);
        Set<String> likedKeys = cleanLiked.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        for (String d : cleanDisliked) {
            if (likedKeys.contains(d.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("«" + d + "» kan ikke både være likt og ikke likt.");
            }
        }
        String cleanNotes = notes == null ? "" : notes.trim();
        if (cleanNotes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Notatene kan være maks " + MAX_NOTES_LENGTH + " tegn.");
        }
        memories.save(new TrainingMemory(userId, cleanLiked, cleanDisliked, cleanNotes));
    }

    /** Tekst til AI-prompten, tom når det ikke er noe å si. */
    @Transactional(readOnly = true)
    public String promptContext(UUID userId, LocalDate today) {
        TrainingMemoryProfile p = profile(userId, today);
        List<String> lines = new ArrayList<>();
        if (!p.liked().isEmpty()) lines.add("- Liker: " + String.join(", ", p.liked()));
        if (!p.disliked().isEmpty()) lines.add("- Liker IKKE (ikke foreslå): " + String.join(", ", p.disliked()));
        if (!p.notes().isBlank()) lines.add("- Brukerens notater: " + p.notes());
        if (p.workoutsLast28Days() > 0) {
            String days = p.usualDays().stream()
                    .map(d -> NORWEGIAN_DAYS.get(DayOfWeek.of(d)))
                    .collect(Collectors.joining(", "));
            lines.add("- Trener i snitt " + p.avgPerWeek() + " økter/uke siste 4 uker"
                    + (days.isEmpty() ? "" : ", oftest " + days) + ".");
        }
        if (!p.topExercises().isEmpty()) {
            lines.add("- Mest brukte øvelser (90 dager): " + String.join(", ", p.topExercises()));
        }
        return lines.isEmpty() ? "" : "\nBrukerminne:\n" + String.join("\n", lines);
    }

    /** Trim, ingen komma (skilletegn i lagringen), ingen duplikater uansett store/små bokstaver. */
    private static List<String> clean(Collection<String> names) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String n : names == null ? List.<String>of() : names) {
            if (n == null) continue;
            String c = n.replace(",", " ").replaceAll("\\s+", " ").trim();
            if (c.isEmpty()) continue;
            if (c.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Øvelsesnavn kan være maks " + MAX_NAME_LENGTH + " tegn.");
            }
            byKey.putIfAbsent(c.toLowerCase(Locale.ROOT), c);
        }
        if (byKey.size() > MAX_EXERCISES) {
            throw new IllegalArgumentException("Maks " + MAX_EXERCISES + " øvelser per liste.");
        }
        return List.copyOf(byKey.values());
    }

    /** Ukedager med minst to økter siste 8 uker og minst halvparten så mange som den vanligste. */
    private static List<Integer> usualDays(List<Workout> recent, LocalDate today) {
        Map<DayOfWeek, Set<LocalDate>> dates = new EnumMap<>(DayOfWeek.class);
        for (Workout w : recent) {
            if (!w.getDate().isBefore(today.minusDays(55))) {
                dates.computeIfAbsent(w.getDate().getDayOfWeek(), d -> new LinkedHashSet<>()).add(w.getDate());
            }
        }
        int max = dates.values().stream().mapToInt(Set::size).max().orElse(0);
        return dates.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2 && e.getValue().size() * 2 >= max)
                .map(e -> e.getKey().getValue())
                .sorted()
                .toList();
    }

    /** Øvelser flest styrkeøkter har inneholdt (telles én gang per økt). */
    private static List<String> topExercises(List<Workout> recent) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (Workout w : recent) {
            if (!"STYRKE".equalsIgnoreCase(w.getType())) continue;
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode b : w.getContent().path("blocks")) {
                if ("superset".equals(b.path("kind").asText(""))) {
                    b.path("exercises").forEach(e -> seen.add(e.path("name").asText("").trim()));
                } else {
                    seen.add(b.path("name").asText("").trim());
                }
            }
            for (String name : seen) {
                if (name.isEmpty()) continue;
                String key = name.toLowerCase(Locale.ROOT);
                counts.merge(key, 1, Integer::sum);
                display.putIfAbsent(key, name);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_EXERCISES)
                .map(e -> display.get(e.getKey()))
                .toList();
    }
}
