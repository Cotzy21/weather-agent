package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.training.NextSetSuggestion.Action;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Progressive overload-motor: regelbasert, ingen LLM, ingen nettverk.
 *
 * Metoden er klassisk DOBBEL PROGRESJON: man holder vekta og øker reps til alle
 * arbeidssettene når toppen av rep-området, SÅ øker man vekta og starter på
 * bunnen av området igjen. Rep-området utledes av hva brukeren faktisk gjør:
 *   ≤5 reps -> 3-5 (styrke) · ≤8 -> 6-8 · ≤12 -> 8-12 (volum) · ellers 12-15.
 *
 * Vektøkning: +5 kg på store beinløft, +2,5 kg ellers, +1 kg under 20 kg
 * (manualer/isolasjon - 2,5 kg der er et for stort hopp i prosent).
 * Står man stille (samme vekt tre økter på rad, ikke flere reps) foreslås
 * deload: 10 % ned og bygg opp igjen. Tommelfingerregler, ikke fasit.
 */
public final class ProgressionAdvisor {

    /** Øvelser som ikke er trent på så lenge, er ikke aktuelle lenger. */
    static final int LOOKBACK_DAYS = 56;
    static final int MAX_SUGGESTIONS = 12;

    private static final int STALL_SESSIONS = 3;
    private static final double DELOAD_FACTOR = 0.9;
    private static final double LIGHT_WEIGHT_KG = 20;

    private static final Set<String> BIG_LOWER_BODY = Set.of(
            "knebøy", "frontbøy", "markløft", "rumensk markløft", "leg press", "hip thrust",
            "squat", "front squat", "deadlift", "romanian deadlift");

    private ProgressionAdvisor() {
    }

    private record SetEntry(int reps, double weightKg) {
    }

    /** Én øvelse i én økt: dato, navnet slik det ble skrevet, og settene. */
    private record Session(LocalDate date, String name, List<SetEntry> sets) {

        double topWeight() {
            return sets.stream().mapToDouble(SetEntry::weightKg).max().orElse(0);
        }

        /** Reps på arbeidssettene - settene med toppvekta (oppvarming telles ikke). */
        List<Integer> workingReps() {
            double top = topWeight();
            return sets.stream()
                    .filter(s -> Math.abs(s.weightKg() - top) < 0.01)
                    .map(SetEntry::reps)
                    .toList();
        }

        int workingRepSum() {
            return workingReps().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Forslag for neste økt, én per øvelse trent de siste {@link #LOOKBACK_DAYS}
     * dagene, sist trente først.
     */
    public static List<NextSetSuggestion> suggest(List<Workout> workouts, LocalDate today, boolean english) {
        Map<String, List<Session>> byExercise = sessionsByExercise(workouts);
        LocalDate cutoff = today.minusDays(LOOKBACK_DAYS);

        List<NextSetSuggestion> out = new ArrayList<>();
        for (List<Session> sessions : byExercise.values()) {
            Session last = sessions.getLast();
            if (last.date().isBefore(cutoff)) {
                continue;
            }
            out.add(suggestFor(sessions, english));
        }
        out.sort(Comparator.comparing(NextSetSuggestion::lastDate).reversed());
        return out.stream().limit(MAX_SUGGESTIONS).toList();
    }

    /** Alle styrkeøkter brutt ned til økter per øvelse, eldst først. */
    private static Map<String, List<Session>> sessionsByExercise(List<Workout> workouts) {
        List<Workout> strength = workouts.stream()
                .filter(w -> "STYRKE".equalsIgnoreCase(w.getType()))
                .sorted(Comparator.comparing(Workout::getDate)
                        .thenComparing(w -> w.getCreatedAt() == null ? Instant.EPOCH : w.getCreatedAt()))
                .toList();

        Map<String, List<Session>> byExercise = new LinkedHashMap<>();
        for (Workout w : strength) {
            for (JsonNode block : w.getContent().path("blocks")) {
                String kind = block.path("kind").asText("exercise");
                if ("superset".equals(kind)) {
                    for (JsonNode ex : block.path("exercises")) {
                        add(byExercise, w.getDate(), ex.path("name").asText(""), sets(ex.path("sets")));
                    }
                } else if ("dropset".equals(kind)) {
                    // Progresjonen følger toppsettet (første drop), ikke nedtrappingen.
                    List<SetEntry> drops = sets(block.path("drops"));
                    add(byExercise, w.getDate(), block.path("name").asText(""),
                            drops.isEmpty() ? drops : List.of(drops.getFirst()));
                } else {
                    add(byExercise, w.getDate(), block.path("name").asText(""), sets(block.path("sets")));
                }
            }
        }
        return byExercise;
    }

    private static void add(Map<String, List<Session>> map, LocalDate date, String name, List<SetEntry> sets) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || sets.isEmpty()) {
            return;
        }
        map.computeIfAbsent(ExerciseCatalog.groupKey(trimmed), k -> new ArrayList<>())
                .add(new Session(date, trimmed, sets));
    }

    private static List<SetEntry> sets(JsonNode array) {
        List<SetEntry> out = new ArrayList<>();
        for (JsonNode s : array) {
            int reps = s.path("reps").asInt(0);
            if (reps > 0) {
                out.add(new SetEntry(reps, s.path("weightKg").asDouble(0)));
            }
        }
        return out;
    }

    private static NextSetSuggestion suggestFor(List<Session> sessions, boolean english) {
        Session last = sessions.getLast();
        double top = last.topWeight();
        List<Integer> working = last.workingReps();
        int maxReps = working.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minReps = working.stream().mapToInt(Integer::intValue).min().orElse(0);

        if (top <= 0) {
            int next = maxReps + 1;
            return new NextSetSuggestion(last.name(), last.date(), 0, working, Action.KROPPSVEKT, 0, next,
                    working.size(), english
                    ? "Bodyweight: aim for %d reps per set. When 15+ feels easy, add weight.".formatted(next)
                    : "Kroppsvekt: sikt på %d reps per sett. Klarer du 15+ lett, legg på vekt.".formatted(next));
        }

        int upper = upperBound(maxReps);
        int lower = lowerBound(upper);
        boolean allAtTop = working.stream().allMatch(r -> r >= upper);

        if (allAtTop) {
            double next = round(top + increment(last.name(), top));
            return new NextSetSuggestion(last.name(), last.date(), top, working, Action.OK_VEKT, next, lower,
                    working.size(), english
                    ? "Every set hit %d reps last time – go up to %s kg and build back up from %d reps."
                            .formatted(upper, kg(next, true), lower)
                    : "Alle settene nådde %d reps sist – øk til %s kg og bygg deg opp fra %d reps igjen."
                            .formatted(upper, kg(next, false), lower));
        }

        if (stalled(sessions)) {
            double next = round(top * DELOAD_FACTOR);
            return new NextSetSuggestion(last.name(), last.date(), top, working, Action.DELOAD, next, upper,
                    working.size(), english
                    ? "Same weight for three sessions without progress – drop 10%% to %s kg and build back up."
                            .formatted(kg(next, true))
                    : "Samme vekt tre økter på rad uten fremgang – ta 10 %% ned til %s kg og bygg opp igjen."
                            .formatted(kg(next, false)));
        }

        int nextReps = Math.min(upper, minReps + 1);
        return new NextSetSuggestion(last.name(), last.date(), top, working, Action.FLERE_REPS, top, nextReps,
                working.size(), english
                ? "Stay at %s kg and aim for %d reps on every set – once all sets reach %d, the weight goes up."
                        .formatted(kg(top, true), nextReps, upper)
                : "Bli på %s kg og sikt på %d reps i alle settene – når alle når %d, øker vi vekta."
                        .formatted(kg(top, false), nextReps, upper));
    }

    /** Samme toppvekt de siste tre øktene og ikke flere reps enn for tre økter siden. */
    private static boolean stalled(List<Session> sessions) {
        if (sessions.size() < STALL_SESSIONS) {
            return false;
        }
        List<Session> recent = sessions.subList(sessions.size() - STALL_SESSIONS, sessions.size());
        double weight = recent.getFirst().topWeight();
        boolean sameWeight = recent.stream().allMatch(s -> Math.abs(s.topWeight() - weight) < 0.01);
        return sameWeight && recent.getLast().workingRepSum() <= recent.getFirst().workingRepSum();
    }

    static int upperBound(int reps) {
        if (reps <= 5) return 5;
        if (reps <= 8) return 8;
        if (reps <= 12) return 12;
        return 15;
    }

    private static int lowerBound(int upper) {
        return switch (upper) {
            case 5 -> 3;
            case 8 -> 6;
            case 12 -> 8;
            default -> 12;
        };
    }

    private static double increment(String exercise, double top) {
        if (top < LIGHT_WEIGHT_KG) {
            return 1;
        }
        return ExerciseCatalog.isHeavyLower(exercise) || BIG_LOWER_BODY.contains(exercise.toLowerCase(Locale.ROOT)) ? 5 : 2.5;
    }

    /** Til nærmeste vekt man faktisk finner i et gym: 0,5 kg under 20 kg, ellers 2,5 kg. */
    private static double round(double kg) {
        double step = kg < LIGHT_WEIGHT_KG ? 0.5 : 2.5;
        return Math.round(kg / step) * step;
    }

    private static String kg(double value, boolean english) {
        String s = value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
        return english ? s : s.replace('.', ',');
    }
}
