package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutCalorieEstimatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double WEIGHT = 80;

    private static Workout workout(String type, String contentJson) {
        try {
            return new Workout(UUID.randomUUID(), LocalDate.now(), "test", type,
                    MAPPER.readTree(contentJson), null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void runningUsesSpeedAsMet() {
        // 10 km på 60 min = 10 km/t -> 10 MET · 80 kg · 1 t = 800 kcal.
        assertEquals(800, WorkoutCalorieEstimator.estimate(
                workout("LØPING", "{\"distanceKm\":10,\"durationMin\":60}"), WEIGHT));
    }

    @Test
    void runningWithoutDurationFallsBackToPerKmRule() {
        // ~1 kcal per kg per km: 80 · 10 = 800.
        assertEquals(800, WorkoutCalorieEstimator.estimate(
                workout("LØPING", "{\"distanceKm\":10}"), WEIGHT));
    }

    @Test
    void hikingAddsAscentWork() {
        // 6 MET · 80 · 2 t = 960, + 0.01 · 80 · 1000 m = 800 -> 1760.
        assertEquals(1760, WorkoutCalorieEstimator.estimate(
                workout("HIKING", "{\"durationMin\":120,\"ascentM\":1000}"), WEIGHT));
    }

    @Test
    void strengthWithoutDurationEstimatesFromSetCount() {
        // 10 sett · 3 min = 30 min -> 5 MET · 80 · 0.5 t = 200 kcal.
        String content = """
                {"blocks":[
                  {"kind":"exercise","name":"Benkpress","sets":[{},{},{},{},{}]},
                  {"kind":"superset","exercises":[
                    {"name":"Curl","sets":[{},{}]},{"name":"Pushdown","sets":[{},{},{}]}]}
                ]}
                """;
        assertEquals(200, WorkoutCalorieEstimator.estimate(workout("STYRKE", content), WEIGHT));
    }

    @Test
    void emptyContentGivesZero() {
        assertEquals(0, WorkoutCalorieEstimator.estimate(workout("FRISTIL", "{}"), WEIGHT));
    }
}
